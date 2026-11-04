package dev.epm.planning.variance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 44 acceptance (AC-F-027-01–02): deterministic amount and percentage variance across
 * Budget/Forecast/prior-period bases on the certified catalog's shared formula version;
 * zero-baseline percentage is UNDEFINED, never infinite; uncertified actual scope cannot enter a
 * certified comparison; null values are governed unavailable results; drill reconciliation is
 * exact; planned KPI values carry formula-version and source lineage; commentary/follow-up actions
 * are governed; the variance view preserves entitlements.
 */
class VarianceCalculationTest {

    private static final String FORMULA = "cash-1.0.0";

    private final VarianceService service = new VarianceService();

    private static VarianceService.VarianceInput input(
            VarianceService.Basis basis, String actual, String plan) {
        return new VarianceService.VarianceInput(
                basis,
                "kpi-cash",
                "ENT-101|TOTAL",
                "2027-P01",
                actual == null ? null : new BigDecimal(actual),
                true,
                plan == null ? null : new BigDecimal(plan),
                FORMULA,
                FORMULA);
    }

    @Test
    void budgetVarianceAmountAndPercentageAreExact() {
        // actual 1100 vs budget 1000: amount +100.00, percentage +0.100000 (+10%).
        VarianceService.VarianceResult result =
                service.variance(input(VarianceService.Basis.BUDGET, "1100.00", "1000.00"));
        assertThat(result.amountVariance()).isEqualByComparingTo("100.00");
        assertThat(result.percentageVariance()).isEqualByComparingTo("0.100000");
        assertThat(result.percentageState()).isEqualTo(VarianceService.PercentageState.COMPUTED);
        assertThat(result.formulaVersion()).isEqualTo(FORMULA);

        // Adverse direction: actual 950 vs budget 1000 → −50.00, −0.050000.
        VarianceService.VarianceResult adverse =
                service.variance(input(VarianceService.Basis.BUDGET, "950.00", "1000.00"));
        assertThat(adverse.amountVariance()).isEqualByComparingTo("-50.00");
        assertThat(adverse.percentageVariance()).isEqualByComparingTo("-0.050000");
    }

    @Test
    void forecastAndPriorPeriodBasesComputeOnTheSameRules() {
        VarianceService.VarianceResult forecast =
                service.variance(input(VarianceService.Basis.FORECAST, "1210.00", "1100.00"));
        assertThat(forecast.amountVariance()).isEqualByComparingTo("110.00");
        assertThat(forecast.percentageVariance()).isEqualByComparingTo("0.100000");

        VarianceService.VarianceResult prior =
                service.variance(input(VarianceService.Basis.PRIOR_PERIOD, "990.00", "900.00"));
        assertThat(prior.amountVariance()).isEqualByComparingTo("90.00");
        assertThat(prior.percentageVariance()).isEqualByComparingTo("0.100000");
    }

    @Test
    void zeroBaselinePercentageIsUndefinedNeverInfinite() {
        VarianceService.VarianceResult zeroPlan =
                service.variance(input(VarianceService.Basis.BUDGET, "50.00", "0.00"));
        assertThat(zeroPlan.amountVariance()).isEqualByComparingTo("50.00");
        assertThat(zeroPlan.percentageState())
                .isEqualTo(VarianceService.PercentageState.UNDEFINED_ZERO_BASELINE);
        assertThat(zeroPlan.percentageVariance()).isNull();

        VarianceService.VarianceResult bothZero =
                service.variance(input(VarianceService.Basis.BUDGET, "0.00", "0.00"));
        assertThat(bothZero.amountVariance()).isEqualByComparingTo("0.00");
        assertThat(bothZero.percentageState())
                .isEqualTo(VarianceService.PercentageState.UNDEFINED_ZERO_BASELINE);
        assertThat(bothZero.percentageVariance()).isNull();
    }

    @Test
    void nullValuesAreGovernedUnavailableResultsNeverCoerced() {
        VarianceService.VarianceResult noActual =
                service.variance(input(VarianceService.Basis.FORECAST, null, "1000.00"));
        assertThat(noActual.percentageState())
                .isEqualTo(VarianceService.PercentageState.UNAVAILABLE);
        assertThat(noActual.note()).contains("never coerced");

        VarianceService.VarianceResult noPlan =
                service.variance(input(VarianceService.Basis.FORECAST, "1000.00", null));
        assertThat(noPlan.percentageState()).isEqualTo(VarianceService.PercentageState.UNAVAILABLE);
        assertThat(noPlan.amountVariance()).isNull();
    }

    @Test
    void uncertifiedActualScopeCannotEnterCertifiedComparison() {
        VarianceService.VarianceInput uncertified =
                new VarianceService.VarianceInput(
                        VarianceService.Basis.BUDGET,
                        "kpi-cash",
                        "ENT-101|TOTAL",
                        "2027-P01",
                        new BigDecimal("1100.00"),
                        false,
                        new BigDecimal("1000.00"),
                        FORMULA,
                        FORMULA);
        VarianceService.VarianceResult result = service.variance(uncertified);
        assertThat(result.percentageState()).isEqualTo(VarianceService.PercentageState.UNAVAILABLE);
        assertThat(result.amountVariance()).isNull();
        assertThat(result.note()).contains("uncertified actual scope");
    }

    @Test
    void sharedFormulaVersionIsEnforcedBetweenActualAndPlan() {
        VarianceService.VarianceInput mixedVersions =
                new VarianceService.VarianceInput(
                        VarianceService.Basis.BUDGET,
                        "kpi-cash",
                        "ENT-101|TOTAL",
                        "2027-P01",
                        new BigDecimal("1100.00"),
                        true,
                        new BigDecimal("1000.00"),
                        "cash-1.0.0",
                        "cash-2.0.0");
        VarianceService.VarianceResult result = service.variance(mixedVersions);
        assertThat(result.percentageState()).isEqualTo(VarianceService.PercentageState.UNAVAILABLE);
        assertThat(result.note()).contains("shared formula version required");
    }

    @Test
    void drillReconciliationIsExactAgainstComponentDetail() {
        VarianceService.VarianceResult line =
                service.variance(input(VarianceService.Basis.BUDGET, "1100.00", "1000.00"));
        VarianceService.DrillReconciliation reconciled =
                service.reconcile(
                        line,
                        List.of(
                                new VarianceService.ComponentVariance(
                                        "ENT-101|DOM", new BigDecimal("80.00")),
                                new VarianceService.ComponentVariance(
                                        "ENT-101|EXP", new BigDecimal("20.00"))));
        assertThat(reconciled.reconciled()).isTrue();
        assertThat(reconciled.componentSum()).isEqualByComparingTo("100.00");

        VarianceService.DrillReconciliation broken =
                service.reconcile(
                        line,
                        List.of(
                                new VarianceService.ComponentVariance(
                                        "ENT-101|DOM", new BigDecimal("80.00")),
                                new VarianceService.ComponentVariance(
                                        "ENT-101|EXP", new BigDecimal("19.99"))));
        assertThat(broken.reconciled()).isFalse();
    }

    @Test
    void plannedKpiValuesCarryFormulaVersionAndSourceLineage() {
        VarianceService.KpiPlanValue planned =
                service.planKpiValue(
                        "plan-bud-2027",
                        "kpi-cash",
                        "ENT-101|TOTAL",
                        "2027-P01",
                        new BigDecimal("1000.004"),
                        FORMULA,
                        "plan-bud-2027:r1/kpi-cash");
        assertThat(planned.formulaVersion()).isEqualTo(FORMULA);
        assertThat(planned.sourceRef()).isEqualTo("plan-bud-2027:r1/kpi-cash");
        assertThat(planned.plannedValue()).isEqualByComparingTo("1000.00");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.planKpiValue(
                                "p", "kpi-cash", "d", "2027-P01", BigDecimal.ONE, null, "ref"));
    }

    @Test
    void commentaryAndFollowUpActionsAreGoverned() {
        VarianceService.CommentaryAction explain =
                service.comment(
                        new VarianceService.CommentaryAction(
                                "var-1",
                                "analyst@epm",
                                "Timing difference on export receipts",
                                "EXPLAIN",
                                null));
        assertThat(explain.action()).isEqualTo("EXPLAIN");

        VarianceService.CommentaryAction followUp =
                service.comment(
                        new VarianceService.CommentaryAction(
                                "var-1",
                                "analyst@epm",
                                "Owner to confirm collection plan",
                                "FOLLOW_UP",
                                "treasury@epm"));
        assertThat(followUp.actionOwner()).isEqualTo("treasury@epm");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.comment(
                                new VarianceService.CommentaryAction(
                                        "var-1", "analyst@epm", " ", "EXPLAIN", null)));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.comment(
                                new VarianceService.CommentaryAction(
                                        "var-1",
                                        "analyst@epm",
                                        "Owner missing",
                                        "FOLLOW_UP",
                                        " ")));
    }

    @Test
    void varianceViewsPreserveEntitlements() {
        List<VarianceService.ViewRow> view =
                service.entitledView(
                        List.of("ENT-101|TOTAL", "ENT-102|TOTAL"), List.of("ENT-101|TOTAL"));
        assertThat(view)
                .anySatisfy(
                        row -> {
                            assertThat(row.dimensionKey()).isEqualTo("ENT-101|TOTAL");
                            assertThat(row.visible()).isTrue();
                        })
                .anySatisfy(
                        row -> {
                            assertThat(row.dimensionKey()).isEqualTo("ENT-102|TOTAL");
                            assertThat(row.visible()).isFalse();
                            assertThat(row.note()).contains("not entitled");
                        });
    }
}
