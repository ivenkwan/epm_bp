package dev.epm.reporting.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Deterministic KPI evaluation over an allowlisted expression registry. Formulas are registered
 * identifiers (sum/gearing/headroom/one) — arbitrary user code cannot reach the evaluator. Null
 * numerators never coerce to zero; zero denominators produce a governed UNAVAILABLE result rather
 * than a value.
 */
@ApplicationScoped
public class KpiEvaluator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_EXPRESSIONS =
            Set.of("sum", "gearing", "headroom", "one");
    private static final MathContext SCALE6 = new MathContext(12, RoundingMode.HALF_UP);

    /** Evaluates a KPI card against an input snapshot; every result is recorded. */
    @Transactional
    public EvaluationResult evaluate(KpiCardEntity card, String inputSnapshotJson) {
        JsonNode input;
        try {
            input = MAPPER.readTree(inputSnapshotJson == null ? "{}" : inputSnapshotJson);
        } catch (java.io.IOException e) {
            throw new KpiCardEntity.SemanticException("unreadable input snapshot");
        }

        if (!ALLOWED_EXPRESSIONS.contains(card.getFormula())) {
            KpiEvaluationEntity.record(
                    card.getKpiId(),
                    card.getVersion(),
                    inputSnapshotJson,
                    null,
                    "UNDEFINED_MEASURE");
            return new EvaluationResult(null, "UNDEFINED_MEASURE", card.getVersion());
        }

        switch (card.getFormula()) {
            case "gearing" -> {
                JsonNode numerator = input.get("numerator");
                JsonNode denominator = input.get("denominator");
                if (numerator == null || denominator == null) {
                    // The mapped measure is absent from the snapshot entirely.
                    return unavailable(card, inputSnapshotJson, "UNDEFINED_MEASURE");
                }
                if (numerator.isNull()) {
                    return unavailable(card, inputSnapshotJson, "UNAVAILABLE_NULL_NUMERATOR");
                }
                if (denominator == null
                        || denominator.isNull()
                        || new BigDecimal(denominator.asText()).signum() == 0) {
                    return unavailable(card, inputSnapshotJson, "UNAVAILABLE_ZERO_DENOMINATOR");
                }
                BigDecimal ratio =
                        new BigDecimal(numerator.asText())
                                .divide(
                                        new BigDecimal(denominator.asText()),
                                        6,
                                        RoundingMode.HALF_UP);
                return ok(card, inputSnapshotJson, ratio);
            }
            case "headroom" -> {
                JsonNode limit = input.get("limit");
                JsonNode utilization = input.get("utilization");
                if (limit == null
                        || utilization == null
                        || limit.isNull()
                        || utilization.isNull()) {
                    return unavailable(card, inputSnapshotJson, "UNDEFINED_MEASURE");
                }
                BigDecimal value =
                        new BigDecimal(limit.asText())
                                .subtract(new BigDecimal(utilization.asText()), SCALE6)
                                .setScale(2, RoundingMode.HALF_UP);
                return ok(card, inputSnapshotJson, value);
            }
            case "sum" -> {
                JsonNode values = input.get("values");
                if (values == null || !values.isArray()) {
                    return unavailable(card, inputSnapshotJson, "UNDEFINED_MEASURE");
                }
                BigDecimal total = BigDecimal.ZERO;
                for (JsonNode value : values) {
                    if (value.isNull()) {
                        return unavailable(card, inputSnapshotJson, "UNAVAILABLE_NULL_NUMERATOR");
                    }
                    total = total.add(new BigDecimal(value.asText()), SCALE6);
                }
                return ok(card, inputSnapshotJson, total.setScale(2, RoundingMode.HALF_UP));
            }
            default -> {
                return unavailable(card, inputSnapshotJson, "UNDEFINED_MEASURE");
            }
        }
    }

    private static EvaluationResult ok(KpiCardEntity card, String snapshot, BigDecimal value) {
        String rendered = value.toPlainString();
        KpiEvaluationEntity.record(card.getKpiId(), card.getVersion(), snapshot, rendered, "OK");
        return new EvaluationResult(rendered, "OK", card.getVersion());
    }

    private static EvaluationResult unavailable(
            KpiCardEntity card, String snapshot, String status) {
        KpiEvaluationEntity.record(card.getKpiId(), card.getVersion(), snapshot, null, status);
        return new EvaluationResult(null, status, card.getVersion());
    }

    /** Evaluated result: value stays null for governed-unavailable outcomes. */
    public record EvaluationResult(String value, String status, int version) {}
}
