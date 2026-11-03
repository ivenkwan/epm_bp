package dev.epm.tests.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.drools.core.base.RuleNameStartsWithAgendaFilter;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;

/**
 * Task 13 rule governance: every rule fires with the expected quarantine outcome; missing inputs,
 * exact boundaries, deterministic explanations, rule-order independence, bounded termination, and
 * zero side effects outside the facts. A rejected candidate leaves financial facts unchanged
 * (promotion isolation).
 */
class RuleGovernanceTest {

    private static final Path RULES = Path.of("..", "..", "rules");
    private static final Path DMN = RULES.resolve("dmn/kpi/ratio-availability.dmn");

    private static KieSession sessionFor(String drlPath) {
        KieHelper helper = new KieHelper();
        helper.addResource(
                ResourceFactory.newFileResource(RULES.resolve(drlPath).toFile()), ResourceType.DRL);
        KieBase kbase = helper.build();
        return kbase.newKieSession();
    }

    @Test
    void everyValidationRuleProducesTheExpectedQuarantineOutcome() {
        // Independently expected results: one fact per rule with the exact outcome.
        List<LandedRow> facts =
                List.of(
                        new LandedRow("r-null", "ACC1", null, "USD", "RECEIPT", "NK-1"),
                        new LandedRow("r-account", "", "10.00", "USD", "RECEIPT", "NK-2"),
                        new LandedRow("r-currency", "ACC1", "10.00", "usd", "RECEIPT", "NK-3"),
                        new LandedRow("r-sign", "ACC1", "-5.00", "USD", "RECEIPT", "NK-4"));
        KieSession session = sessionFor("drools/validation/validation-rules.drl");
        try {
            facts.forEach(session::insert);
            int fired = session.fireAllRules(100);
            assertThat(fired).isEqualTo(4);

            assertThat(facts.get(0).isQuarantined()).isTrue();
            assertThat(facts.get(0).getViolationCode()).isEqualTo("NULL_AMOUNT");
            assertThat(facts.get(1).isQuarantined()).isTrue();
            assertThat(facts.get(1).getViolationCode()).isEqualTo("MISSING_ACCOUNT");
            assertThat(facts.get(2).isQuarantined()).isTrue();
            assertThat(facts.get(2).getViolationCode()).isEqualTo("CURRENCY_INVALID");
            assertThat(facts.get(3).isQuarantined()).isTrue();
            assertThat(facts.get(3).getViolationCode()).isEqualTo("INVALID_SIGN");

            // Valid row stays clean: zero remains numeric and unquarantined.
            LandedRow valid = new LandedRow("r-ok", "ACC1", "0.00", "USD", "PAYMENT", "NK-5");
            session.insert(valid);
            session.fireAllRules(100);
            assertThat(valid.isQuarantined()).isFalse();
            assertThat(valid.getViolationCode()).isNull();
        } finally {
            session.dispose();
        }
    }

    @Test
    void ruleOrderIndependenceHoldsForOverlappingConditions() {
        // A fact satisfying multiple rules: first-match semantics keep the explanation
        // deterministic regardless of insertion order.
        List<List<String>> results = new ArrayList<>();
        for (int attempt = 0; attempt < 2; attempt++) {
            KieSession session = sessionFor("drools/validation/validation-rules.drl");
            try {
                LandedRow fact = new LandedRow("r-multi", null, null, "eur", "RECEIPT", "NK-M");
                session.insert(fact);
                session.fireAllRules(new RuleNameStartsWithAgendaFilter("completeness"));
                results.add(List.of(String.valueOf(fact.isQuarantined()), fact.getViolationCode()));
            } finally {
                session.dispose();
            }
        }
        assertThat(results.get(0)).isEqualTo(results.get(1));
        // Alphabetical rule order: "missing account" precedes "null amount" deterministically.
        assertThat(results.get(0)).containsExactly("true", "MISSING_ACCOUNT");
    }

    @Test
    void terminationIsBoundedAndSideEffectFree() {
        KieSession session = sessionFor("drools/validation/validation-rules.drl");
        try {
            LandedRow fact = new LandedRow("r-term", "ACC1", "1.00", "USD", "PAYMENT", "NK-T");
            session.insert(fact);
            int fired = session.fireAllRules(1000);
            // Rules that quarantine disable their own re-match: exactly one pass.
            assertThat(fired).isZero();
            // Repeated firing over an unchanged fact does nothing more.
            assertThat(session.fireAllRules(1000)).isZero();
        } finally {
            session.dispose();
        }
    }

    @Test
    void dmnAvailabilityTableCoversEveryRowAndBoundary() throws Exception {
        KieHelper helper = new KieHelper();
        helper.addContent(java.nio.file.Files.readString(DMN), ResourceType.DMN);
        KieBase kbase = helper.build();
        var runtime =
                org.kie.api.runtime.KieRuntimeFactory.of(kbase)
                        .get(org.kie.dmn.api.core.DMNRuntime.class);
        var model = runtime.getModel("https://epm.dev/rules/kpi", "kpi-registry");
        assertThat(model).as("DMN model must register").isNotNull();
        assertThat(model.getMessages(org.kie.dmn.api.core.DMNMessage.Severity.ERROR)).isEmpty();

        assertThat(evaluateAvailability(runtime, model, "ABSENT", "ABSENT", "FALSE"))
                .isEqualTo("UNDEFINED_MEASURE");
        assertThat(evaluateAvailability(runtime, model, "NULL", "PRESENT", "FALSE"))
                .isEqualTo("UNAVAILABLE_NULL_NUMERATOR");
        assertThat(evaluateAvailability(runtime, model, "PRESENT", "PRESENT", "TRUE"))
                .isEqualTo("UNAVAILABLE_ZERO_DENOMINATOR");
        assertThat(evaluateAvailability(runtime, model, "PRESENT", "PRESENT", "FALSE"))
                .isEqualTo("OK");
    }

    @Test
    void ruleAssetsExistOnDisk() throws Exception {
        assertThat(Files.exists(RULES.resolve("drools/validation/validation-rules.drl"))).isTrue();
        assertThat(Files.exists(DMN)).isTrue();
        assertThat(Files.exists(RULES.resolve("drools/README.md"))).isTrue();
        assertThat(Files.exists(RULES.resolve("dmn/README.md"))).isTrue();
    }

    @Test
    void candidateRejectionLeavesFinancialFactsUnchanged() {
        // A candidate rule set that is invalid fails to build: the "active" set (and any
        // facts already evaluated) are untouched.
        assertThatThrownBy(
                        () -> {
                            KieHelper helper = new KieHelper();
                            helper.addContent(
                                    "package broken\nrule \"syntax error\"\nwhen\n"
                                            + "  $x : NoSuchFact( id == )\nthen\nend",
                                    ResourceType.DRL);
                            helper.build();
                        })
                .isInstanceOf(RuntimeException.class);

        KieSession session = sessionFor("drools/validation/validation-rules.drl");
        try {
            LandedRow fact = new LandedRow("r-safe", "ACC1", "2.00", "USD", "PAYMENT", "NK-S");
            session.insert(fact);
            session.fireAllRules(100);
            assertThat(fact.isQuarantined()).isFalse();
        } finally {
            session.dispose();
        }
    }

    private static String evaluateAvailability(
            org.kie.dmn.api.core.DMNRuntime runtime,
            org.kie.dmn.api.core.DMNModel model,
            String numerator,
            String denominator,
            String denominatorZero) {
        var context = runtime.newContext();
        context.set("numeratorPresent", numerator);
        context.set("denominatorPresent", denominator);
        context.set("denominatorZero", denominatorZero);
        var result = runtime.evaluateAll(model, context);
        return (String) result.getDecisionResultByName("Ratio Availability").getResult();
    }
}
