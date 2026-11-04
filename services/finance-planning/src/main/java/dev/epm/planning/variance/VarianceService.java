package dev.epm.planning.variance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Task 44 deterministic variance and shared-KPI planning: Actual versus
 * Budget/Forecast/prior-period comparisons on the certified semantic catalog's shared formula
 * version. Uncertified actual scope cannot enter a certified comparison; null values are
 * unavailable (never coerced); a zero-baseline percentage variance is UNDEFINED, never infinite.
 * Planned KPI values carry the same formula version as actuals plus explicit source/version
 * lineage. Amount variance reconciles exactly with its component detail.
 */
public class VarianceService {

    private static final int AMOUNT_SCALE = 2;
    private static final int PERCENT_SCALE = 6;

    /** Comparison basis. */
    public enum Basis {
        BUDGET,
        FORECAST,
        PRIOR_PERIOD
    }

    /** Governed percentage state. */
    public enum PercentageState {
        COMPUTED,
        UNDEFINED_ZERO_BASELINE,
        UNAVAILABLE
    }

    /** One variance input line (actual from certified data, plan from a locked plan). */
    public record VarianceInput(
            Basis basis,
            String kpiId,
            String dimensionKey,
            String period,
            BigDecimal actualValue,
            boolean actualCertified,
            BigDecimal planValue,
            String actualFormulaVersion,
            String planFormulaVersion) {}

    /** The variance outcome. */
    public record VarianceResult(
            Basis basis,
            String kpiId,
            String dimensionKey,
            String period,
            BigDecimal actualValue,
            BigDecimal planValue,
            BigDecimal amountVariance,
            BigDecimal percentageVariance,
            PercentageState percentageState,
            String formulaVersion,
            String note) {

        static VarianceResult unavailable(VarianceInput input, PercentageState state, String note) {
            return new VarianceResult(
                    input.basis(),
                    input.kpiId(),
                    input.dimensionKey(),
                    input.period(),
                    input.actualValue(),
                    input.planValue(),
                    null,
                    null,
                    state,
                    input.actualFormulaVersion(),
                    note);
        }
    }

    /** Computes one variance line under the governed rules. */
    public VarianceResult variance(VarianceInput input) {
        if (!input.actualCertified()) {
            return VarianceResult.unavailable(
                    input,
                    PercentageState.UNAVAILABLE,
                    "uncertified actual scope cannot enter a certified comparison");
        }
        if (input.actualFormulaVersion() == null
                || !input.actualFormulaVersion().equals(input.planFormulaVersion())) {
            return VarianceResult.unavailable(
                    input,
                    PercentageState.UNAVAILABLE,
                    "shared formula version required: actual "
                            + input.actualFormulaVersion()
                            + " vs plan "
                            + input.planFormulaVersion());
        }
        if (input.actualValue() == null || input.planValue() == null) {
            return VarianceResult.unavailable(
                    input,
                    PercentageState.UNAVAILABLE,
                    "null values are unavailable results and are never coerced");
        }
        BigDecimal amount =
                input.actualValue()
                        .subtract(input.planValue())
                        .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        PercentageState state;
        BigDecimal percentage;
        if (input.planValue().signum() == 0) {
            state = PercentageState.UNDEFINED_ZERO_BASELINE;
            percentage = null;
        } else {
            state = PercentageState.COMPUTED;
            percentage =
                    amount.divide(input.planValue().abs(), PERCENT_SCALE, RoundingMode.HALF_UP);
        }
        return new VarianceResult(
                input.basis(),
                input.kpiId(),
                input.dimensionKey(),
                input.period(),
                input.actualValue().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                input.planValue().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                amount,
                percentage,
                state,
                input.actualFormulaVersion(),
                null);
    }

    /** A planned KPI value with the same formula version as actuals plus lineage. */
    public record KpiPlanValue(
            String planId,
            String kpiId,
            String dimensionKey,
            String period,
            BigDecimal plannedValue,
            String formulaVersion,
            String sourceRef) {}

    /** Validated planned KPI value; formula version must equal the catalog's actual version. */
    public KpiPlanValue planKpiValue(
            String planId,
            String kpiId,
            String dimensionKey,
            String period,
            BigDecimal plannedValue,
            String catalogFormulaVersion,
            String sourceRef) {
        if (catalogFormulaVersion == null) {
            throw new IllegalArgumentException(
                    "KPI " + kpiId + " has no certified formula version in the catalog");
        }
        return new KpiPlanValue(
                planId,
                kpiId,
                dimensionKey,
                period,
                plannedValue.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                catalogFormulaVersion,
                sourceRef);
    }

    /** Component of a drill reconciliation. */
    public record ComponentVariance(String componentKey, BigDecimal amountVariance) {}

    /**
     * Drill reconciliation: the line variance amount must equal the sum of its component variances
     * exactly (within the declared amount scale).
     */
    public record DrillReconciliation(
            BigDecimal lineVariance, BigDecimal componentSum, boolean reconciled) {}

    public DrillReconciliation reconcile(VarianceResult line, List<ComponentVariance> components) {
        if (line.amountVariance() == null) {
            throw new IllegalArgumentException(
                    "line variance is unavailable; nothing to reconcile");
        }
        BigDecimal sum =
                components.stream()
                        .map(ComponentVariance::amountVariance)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        return new DrillReconciliation(
                line.amountVariance(), sum, line.amountVariance().compareTo(sum) == 0);
    }

    /** Commentary/follow-up action recorded against a variance line. */
    public record CommentaryAction(
            String varianceId, String author, String body, String action, String actionOwner) {}

    /** Records commentary; actions require an owner when they are FOLLOW_UP. */
    public CommentaryAction comment(CommentaryAction action) {
        if (action.body() == null || action.body().isBlank()) {
            throw new IllegalArgumentException("commentary body must be nonblank");
        }
        if ("FOLLOW_UP".equals(action.action())
                && (action.actionOwner() == null || action.actionOwner().isBlank())) {
            throw new IllegalArgumentException("follow-up actions require a named owner");
        }
        return action;
    }

    /** Entitlement-preserving variance view: unentitled dimensions labelled, not leaked. */
    public record ViewRow(String dimensionKey, boolean visible, String note) {}

    public List<ViewRow> entitledView(List<String> dimensionKeys, List<String> entitledDimensions) {
        return dimensionKeys.stream()
                .map(
                        key -> {
                            boolean visible = entitledDimensions.contains(key);
                            return new ViewRow(
                                    key,
                                    visible,
                                    visible ? null : "not entitled: variance detail unavailable");
                        })
                .toList();
    }
}
