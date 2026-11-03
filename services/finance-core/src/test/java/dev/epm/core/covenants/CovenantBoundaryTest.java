package dev.epm.core.covenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Task 26 golden tests (§3, hand-calculated) plus the exact-boundary, zero-denominator, and
 * formula-versioning cases. The DMN availability table (rules/dmn/treasury/covenants.dmn) is
 * exercised through the shared rule harness.
 */
@QuarkusTest
class CovenantBoundaryTest {

    @Inject CovenantCalculator covenants;

    @Test
    void reproducesGoldenCovenantResults() {
        // Golden §3 COV001/ENT101: 0.500000 vs 0.600000 → headroom 0.100000 COMPLIANT.
        CovenantCalculator.Result cov001 =
                covenants.evaluate(
                        new CovenantCalculator.Input(
                                "COV001", "ENT101", "850000.00", "1700000.00", "0.600000"));
        assertThat(cov001.ratio()).isEqualTo("0.500000");
        assertThat(cov001.headroom()).isEqualTo("0.100000");
        assertThat(cov001.status()).isEqualTo("COMPLIANT");
        assertThat(cov001.roundingMode()).isEqualTo("HALF_UP");
        assertThat(cov001.scale()).isEqualTo(6);

        // Golden §3 COV002/ENT102: 1.200000 vs inclusive 1.200000 → COMPLIANT_AT_BOUNDARY
        // semantics: at-threshold is compliant with exactly zero headroom.
        CovenantCalculator.Result cov002 =
                covenants.evaluate(
                        new CovenantCalculator.Input(
                                "COV002", "ENT102", "600000.00", "500000.00", "1.200000"));
        assertThat(cov002.ratio()).isEqualTo("1.200000");
        assertThat(cov002.headroom()).isEqualTo("0.000000");
        assertThat(cov002.status()).isEqualTo("COMPLIANT");
    }

    @Test
    void exactThresholdBoundaryIsInclusiveAndOneMicroStepBeyondBreaches() {
        CovenantCalculator.Result at =
                covenants.evaluate(
                        new CovenantCalculator.Input(
                                "B1", "ENT101", "600000.00", "1000000.00", "0.600000"));
        assertThat(at.status())
                .as("ratio exactly at threshold is compliant")
                .isEqualTo("COMPLIANT");
        assertThat(at.headroom()).isEqualTo("0.000000");

        CovenantCalculator.Result beyond =
                covenants.evaluate(
                        new CovenantCalculator.Input(
                                "B2", "ENT101", "600001.00", "1000000.00", "0.600000"));
        assertThat(beyond.status()).isEqualTo("BREACH");
        assertThat(beyond.headroom()).isEqualTo("-0.000001");
    }

    @Test
    void zeroDenominatorIsInvalidNotComputableNeverZeroOrBreach() {
        CovenantCalculator.Result zero =
                covenants.evaluate(
                        new CovenantCalculator.Input(
                                "Z1", "ENT103", "300000.00", "0.00", "0.600000"));
        assertThat(zero.status()).isEqualTo("INVALID_NOT_COMPUTABLE");
        assertThat(zero.ratio()).as("ratio stays null, never coerced to 0.000000").isNull();
        assertThat(zero.headroom()).isNull();
    }

    @Test
    void nullNumeratorIsRejectedAsValidationQuarantineNotZero() {
        // A null numerator quarantines upstream (Task 19 NULL_AMOUNT); the calculator
        // never sees a null here — documented and asserted as the contract.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                covenants.evaluate(
                                        new CovenantCalculator.Input(
                                                "N1", "ENT101", null, "100.00", "0.600000")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void dmnAvailabilityTableAgreesWithTheCalculator() throws Exception {
        // The DMN table (cov registry) encodes the zero-denominator and threshold
        // classification; drive it through the rule harness for row coverage.
        var helper = new org.kie.internal.utils.KieHelper();
        helper.addContent(
                java.nio.file.Files.readString(
                        java.nio.file.Path.of(
                                "..", "..", "rules", "dmn", "treasury", "covenants.dmn")),
                org.kie.api.io.ResourceType.DMN);
        var kbase = helper.build();
        var runtime =
                org.kie.api.runtime.KieRuntimeFactory.of(kbase)
                        .get(org.kie.dmn.api.core.DMNRuntime.class);
        var model = runtime.getModel("https://epm.dev/rules/covenant", "covenant-status");
        assertThat(model).isNotNull();

        // Zero-denominator row: INVALID (never a breach).
        assertThat(evaluateDmn(runtime, model, "FALSE", "-", "-"))
                .isEqualTo("INVALID_NOT_COMPUTABLE");
        // Compliant row: calculator semantics (inclusive threshold).
        assertThat(evaluateDmn(runtime, model, "TRUE", "0.500000", "0.600000"))
                .isEqualTo("COMPLIANT");
    }

    private static String evaluateDmn(
            org.kie.dmn.api.core.DMNRuntime runtime,
            org.kie.dmn.api.core.DMNModel model,
            String denominatorPresent,
            String ratio,
            String threshold) {
        var context = runtime.newContext();
        context.set("denominatorPresent", denominatorPresent);
        context.set("ratio", ratio);
        context.set("threshold", threshold);
        return (String)
                runtime.evaluateAll(model, context)
                        .getDecisionResultByName("Covenant Status")
                        .getResult();
    }
}
