package dev.epm.planning.statements;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Task 42 acceptance (AC-F-025-01–03, playbook 8): the deterministic integrated calculation
 * reproduces the independently hand-calculated golden statements (fixtures §8) exactly;
 * recalculation is linked across P&amp;L/BS/CF; cash links to cash flow; schedule-to-statement
 * lineage is preserved; imbalance carries exact coordinates and blocks submission via a case whose
 * closure never approves; zero participates while null is a required-input error; failed
 * calculations preserve the last valid statements; replay and concurrent recalculation are
 * identical.
 */
class IntegratedStatementTest {

    private static final String ENTITY = "ENT-101";
    private static final String PERIOD = "2027-P01";

    private final IntegratedStatementService engine = new IntegratedStatementService();
    private final dev.epm.planning.schedules.ScheduleService schedules =
            new dev.epm.planning.schedules.ScheduleService();

    /** Golden input set (fixtures §8). */
    private static List<IntegratedStatementService.ScheduleLine> goldenInputs() {
        return List.of(
                line("REVENUE", "revenue.domestic", "1000.00"),
                line("REVENUE", "revenue.export", "500.00"),
                line("OPERATING_COST", "opcost.salaries", "400.00"),
                line("OPERATING_COST", "opcost.other", "100.00"),
                line("CAPITAL", "capex", "300.00"),
                line("CAPITAL", "depreciation", "50.00"),
                line("OPENING_BALANCE", "opening.cash", "2000.00"),
                line("OPENING_BALANCE", "opening.equity", "2000.00"));
    }

    private static IntegratedStatementService.ScheduleLine line(
            String type, String key, String value) {
        return new IntegratedStatementService.ScheduleLine(
                type, ENTITY, PERIOD, key, value == null ? null : new BigDecimal(value));
    }

    @Test
    void goldenStatementsAreReproducedExactlyFromTheFixtures() throws Exception {
        List<IntegratedStatementService.ScheduleLine> inputs = readGoldenInputs();
        IntegratedStatementService.CalculationOutcome outcome = engine.calculate(inputs, null);
        assertThat(outcome.status()).isEqualTo(IntegratedStatementService.Status.CALCULATED);

        List<String> actual =
                flatten(outcome.statements()).stream()
                        .map(this::goldenRow)
                        .sorted()
                        .collect(Collectors.toList());
        List<String> expected =
                Files.readAllLines(expectedPath()).stream()
                        .skip(1)
                        .filter(row -> !row.isBlank())
                        .sorted()
                        .collect(Collectors.toList());
        assertThat(actual)
                .as("calculator reproduces fixtures §8 exactly")
                .containsExactlyElementsOf(expected);
    }

    @Test
    void recalculationIsLinkedAcrossAllThreeStatements() {
        IntegratedStatementService.CalculationOutcome base = engine.calculate(goldenInputs(), null);
        List<IntegratedStatementService.ScheduleLine> changed = new ArrayList<>(goldenInputs());
        changed.set(0, line("REVENUE", "revenue.domestic", "1200.00"));
        IntegratedStatementService.CalculationOutcome recalculated =
                engine.calculate(changed, null);

        assertThat(pl(recalculated, "revenue_total")).isEqualByComparingTo("1700.00");
        assertThat(pl(recalculated, "net_income"))
                .isEqualByComparingTo("900.00"); // ebit 1200, tax 300
        assertThat(cf(recalculated, "cash_flow_total")).isEqualByComparingTo("650.00");
        assertThat(bs(recalculated, "closing_cash")).isEqualByComparingTo("2650.00");
        assertThat(bs(recalculated, "balance_difference")).isEqualByComparingTo("0.00");
        // Every downstream figure moved; base stays untouched (pure function).
        assertThat(pl(base, "revenue_total")).isEqualByComparingTo("1500.00");
    }

    @Test
    void cashToCashFlowLinkageHoldsExactly() {
        IntegratedStatementService.Statements statements =
                engine.calculate(goldenInputs(), null).statements();
        assertThat(engine.cashLinkageHolds(statements, new BigDecimal("2000.00"))).isTrue();
        assertThat(bs(engine.calculate(goldenInputs(), null), "closing_cash"))
                .isEqualByComparingTo("2500.00");
    }

    @Test
    void scheduleToStatementLineageIsPreservedAndDrillable() {
        IntegratedStatementService.Statements statements =
                engine.calculate(goldenInputs(), null).statements();
        List<String> revenueLineage = engine.drillLineage(statements, "revenue_total");
        assertThat(revenueLineage)
                .containsExactly(
                        "REVENUE/" + ENTITY + "/" + PERIOD + "/revenue.domestic",
                        "REVENUE/" + ENTITY + "/" + PERIOD + "/revenue.export");
        List<String> closingCashLineage = engine.drillLineage(statements, "closing_cash");
        assertThat(closingCashLineage)
                .contains(
                        "OPENING_BALANCE/" + ENTITY + "/" + PERIOD + "/opening.cash", "cash_flow");
    }

    @Test
    void imbalanceCarriesExactCoordinatesAndBlocksSubmissionWithACase() {
        List<IntegratedStatementService.ScheduleLine> unbalancedOpening =
                new ArrayList<>(goldenInputs());
        unbalancedOpening.set(7, line("OPENING_BALANCE", "opening.equity", "1900.00"));
        IntegratedStatementService.Statements lastValid =
                engine.calculate(goldenInputs(), null).statements();
        IntegratedStatementService.CalculationOutcome imbalanced =
                engine.calculate(unbalancedOpening, lastValid);

        assertThat(imbalanced.status()).isEqualTo(IntegratedStatementService.Status.IMBALANCE);
        assertThat(imbalanced.statements().balanceDifference()).isEqualByComparingTo("100.00");

        dev.epm.planning.schedules.ScheduleService.SubmitOutcome blocked =
                schedules.submitForApproval(
                        "plan-bud-2027",
                        ENTITY,
                        PERIOD,
                        "sched-opening",
                        imbalanced,
                        "case-imb-1",
                        Instant.parse("2026-11-04T09:00:00Z"));
        assertThat(blocked.submitted()).isFalse();
        dev.epm.planning.schedules.ScheduleService.ImbalanceCase opened = blocked.openedCase();
        assertThat(opened).isNotNull();
        assertThat(opened.statementType()).isEqualTo("BS");
        assertThat(opened.period()).isEqualTo(PERIOD);
        assertThat(opened.entityId()).isEqualTo(ENTITY);
        assertThat(opened.scheduleId()).isEqualTo("sched-opening");
        assertThat(opened.difference()).isEqualTo("100.00");
        assertThat(opened.modelVersion()).isEqualTo(IntegratedStatementService.MODEL_VERSION);
        assertThat(opened.ruleVersion()).isEqualTo(IntegratedStatementService.RULE_VERSION);
        assertThat(opened.calcTrace()).contains("rule=" + IntegratedStatementService.RULE_VERSION);
    }

    @Test
    void caseClosureNeverApprovesOrPublishesThePlan() {
        dev.epm.planning.schedules.ScheduleService.ImbalanceCase open =
                new dev.epm.planning.schedules.ScheduleService.ImbalanceCase(
                        "case-imb-2",
                        "plan-bud-2027",
                        ENTITY,
                        PERIOD,
                        "BS",
                        "sched-opening",
                        "100.00",
                        IntegratedStatementService.MODEL_VERSION,
                        IntegratedStatementService.RULE_VERSION,
                        "trace",
                        "OPEN",
                        Instant.now());
        dev.epm.planning.schedules.ScheduleService.CaseClosure closure =
                schedules.closeCase(open, "controller@epm", "DRAFT");
        assertThat(closure.closedCase().status()).isEqualTo("CLOSED");
        assertThat(closure.planStatusAfter()).isEqualTo("DRAFT");
        assertThat(closure.note()).contains("never approves or publishes");
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> schedules.closeCase(closure.closedCase(), "controller@epm", "DRAFT"));
    }

    @Test
    void zeroParticipatesWhileNullIsARequiredInputError() {
        List<IntegratedStatementService.ScheduleLine> withTrueZero =
                new ArrayList<>(goldenInputs());
        withTrueZero.set(3, line("OPERATING_COST", "opcost.other", "0.00"));
        IntegratedStatementService.CalculationOutcome zeroOutcome =
                engine.calculate(withTrueZero, null);
        assertThat(zeroOutcome.status()).isEqualTo(IntegratedStatementService.Status.CALCULATED);
        assertThat(pl(zeroOutcome, "opex_total")).isEqualByComparingTo("400.00");
        // revenue 1500 − opex 400 = ebit 1100, tax 275, net income 825.
        assertThat(pl(zeroOutcome, "net_income")).isEqualByComparingTo("825.00");

        List<IntegratedStatementService.ScheduleLine> withNull = new ArrayList<>(goldenInputs());
        withNull.set(3, line("OPERATING_COST", "opcost.other", null));
        IntegratedStatementService.Statements lastValid =
                engine.calculate(goldenInputs(), null).statements();
        IntegratedStatementService.CalculationOutcome nullOutcome =
                engine.calculate(withNull, lastValid);
        assertThat(nullOutcome.status())
                .isEqualTo(IntegratedStatementService.Status.MISSING_REQUIRED_INPUT);
        assertThat(nullOutcome.errorCoordinate())
                .isEqualTo("OPERATING_COST/" + ENTITY + "/" + PERIOD + "/opcost.other");
        assertThat(nullOutcome.note()).contains("never coerced to zero");
    }

    @Test
    void failedCalculationsPreserveTheLastValidStatements() {
        IntegratedStatementService.Statements lastValid =
                engine.calculate(goldenInputs(), null).statements();
        List<IntegratedStatementService.ScheduleLine> broken =
                List.of(line("REVENUE", "revenue.domestic", "1.00"));
        IntegratedStatementService.CalculationOutcome failed = engine.calculate(broken, lastValid);
        assertThat(failed.status())
                .isEqualTo(IntegratedStatementService.Status.MISSING_REQUIRED_INPUT);
        assertThat(failed.previousValidStatements()).isSameAs(lastValid);
        assertThat(failed.previousValidStatements().balanceDifference())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void replayAndConcurrentRecalculationAreIdentical() throws Exception {
        IntegratedStatementService.CalculationOutcome first =
                engine.calculate(goldenInputs(), null);
        IntegratedStatementService.CalculationOutcome replay =
                engine.calculate(goldenInputs(), null);
        assertThat(replay.calcTrace()).isEqualTo(first.calcTrace());
        assertThat(flatten(replay.statements())).isEqualTo(flatten(first.statements()));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> jobs =
                    List.of(
                            () -> engine.calculate(goldenInputs(), null).calcTrace(),
                            () -> engine.calculate(goldenInputs(), null).calcTrace(),
                            () -> engine.calculate(goldenInputs(), null).calcTrace(),
                            () -> engine.calculate(goldenInputs(), null).calcTrace());
            List<Future<String>> results = pool.invokeAll(jobs);
            for (Future<String> result : results) {
                assertThat(result.get()).isEqualTo(first.calcTrace());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void governedDmnIntegrityTableAgreesWithTheEnginePolicy() throws Exception {
        var helper = new org.kie.internal.utils.KieHelper();
        helper.addContent(Files.readString(dmnPath()), org.kie.api.io.ResourceType.DMN);
        var kbase = helper.build();
        var runtime =
                org.kie.api.runtime.KieRuntimeFactory.of(kbase)
                        .get(org.kie.dmn.api.core.DMNRuntime.class);
        var model = runtime.getModel("https://epm.dev/rules/planning", "integrated-statements");
        assertThat(model).isNotNull();
        assertThat(model.getMessages(org.kie.dmn.api.core.DMNMessage.Severity.ERROR)).isEmpty();

        assertThat(integrity(runtime, model, false, false)).isEqualTo("MISSING_REQUIRED_INPUT");
        assertThat(integrity(runtime, model, true, false)).isEqualTo("IMBALANCE");
        assertThat(integrity(runtime, model, true, true)).isEqualTo("PASS");
        // Engine outcomes line up with the governed table for the same facts
        // (engine CALCULATED is the governed table's PASS).
        assertThat(dmnWord(engine.calculate(goldenInputs(), null).status()))
                .isEqualTo(integrity(runtime, model, true, true));
        List<IntegratedStatementService.ScheduleLine> withNull = new ArrayList<>(goldenInputs());
        withNull.set(0, line("REVENUE", "revenue.domestic", null));
        assertThat(dmnWord(engine.calculate(withNull, null).status()))
                .isEqualTo(integrity(runtime, model, false, false));
    }

    private static String integrity(
            org.kie.dmn.api.core.DMNRuntime runtime,
            org.kie.dmn.api.core.DMNModel model,
            boolean inputsPresent,
            boolean balanced) {
        var context = runtime.newContext();
        context.set("requiredInputsPresent", inputsPresent);
        context.set("statementsBalanced", balanced);
        return (String)
                runtime.evaluateAll(model, context)
                        .getDecisionResultByName("Statement Integrity")
                        .getResult();
    }

    /** Engine status expressed in the governed DMN table's vocabulary. */
    private static String dmnWord(IntegratedStatementService.Status status) {
        return status == IntegratedStatementService.Status.CALCULATED ? "PASS" : status.name();
    }

    private static BigDecimal pl(IntegratedStatementService.CalculationOutcome o, String key) {
        return amount(o.statements().profitAndLoss(), key);
    }

    private static BigDecimal bs(IntegratedStatementService.CalculationOutcome o, String key) {
        return amount(o.statements().balanceSheet(), key);
    }

    private static BigDecimal cf(IntegratedStatementService.CalculationOutcome o, String key) {
        return amount(o.statements().cashFlow(), key);
    }

    private static BigDecimal amount(
            List<IntegratedStatementService.StatementLine> lines, String key) {
        return lines.stream()
                .filter(l -> l.lineKey().equals(key))
                .findFirst()
                .orElseThrow()
                .amount();
    }

    private List<IntegratedStatementService.StatementLine> flatten(
            IntegratedStatementService.Statements statements) {
        List<IntegratedStatementService.StatementLine> all = new ArrayList<>();
        all.addAll(statements.profitAndLoss());
        all.addAll(statements.balanceSheet());
        all.addAll(statements.cashFlow());
        return all;
    }

    private String goldenRow(IntegratedStatementService.StatementLine line) {
        return String.format(
                "%s,%s,%s,%s,%s,%s",
                ENTITY,
                PERIOD,
                line.statementType(),
                line.lineKey(),
                line.amount().toPlainString(),
                IntegratedStatementService.RULE_VERSION);
    }

    private static List<IntegratedStatementService.ScheduleLine> readGoldenInputs()
            throws IOException {
        return Files.readAllLines(inputsPath()).stream()
                .skip(1)
                .filter(row -> !row.isBlank())
                .map(
                        row -> {
                            String[] f = row.split(",");
                            return new IntegratedStatementService.ScheduleLine(
                                    f[0],
                                    f[1],
                                    f[2],
                                    f[3],
                                    f[4].isBlank() ? null : new BigDecimal(f[4]));
                        })
                .toList();
    }

    private static Path inputsPath() {
        return firstExisting(
                Path.of("db", "fixtures", "planning", "integrated-statement-inputs.csv"),
                Path.of(
                        "..",
                        "..",
                        "db",
                        "fixtures",
                        "planning",
                        "integrated-statement-inputs.csv"));
    }

    private static Path expectedPath() {
        return firstExisting(
                Path.of("db", "fixtures", "expected", "expected-integrated-statements.csv"),
                Path.of(
                        "..",
                        "..",
                        "db",
                        "fixtures",
                        "expected",
                        "expected-integrated-statements.csv"));
    }

    private static Path dmnPath() {
        return firstExisting(
                Path.of("rules", "dmn", "planning", "integrated-statements.dmn"),
                Path.of("..", "..", "rules", "dmn", "planning", "integrated-statements.dmn"));
    }

    private static Path firstExisting(Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("no fixture candidate found");
    }
}
