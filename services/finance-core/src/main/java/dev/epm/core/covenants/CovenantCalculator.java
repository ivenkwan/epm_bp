package dev.epm.core.covenants;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Versioned covenant monitoring (Task 26). Ratios are fixed-precision (scale 6, HALF_UP) with
 * inclusive thresholds; a zero denominator is a governed INVALID_NOT_COMPUTABLE — never zero, never
 * a legal breach claim. The platform surfaces headroom and evidence; interpretation and external
 * communication stay with named human Treasury authority.
 */
@ApplicationScoped
public class CovenantCalculator {

    /** Formula-card version (pinned against the §3 goldens and the DMN table). */
    public static final String VERSION = "cov-1.0.0";

    /** Covenant evaluation input against a versioned formula card. */
    public record Input(
            String covenantId,
            String entityId,
            String numerator,
            String denominator,
            String threshold) {}

    /** Evaluated covenant with visible headroom and status. */
    public record Result(
            String covenantId,
            String entityId,
            String ratio,
            String threshold,
            String headroom,
            String status,
            String roundingMode,
            int scale) {}

    /** Evaluates one covenant against its threshold (inclusive comparison). */
    public Result evaluate(Input input) {
        BigDecimal denominator = new BigDecimal(input.denominator());
        if (denominator.signum() == 0) {
            return new Result(
                    input.covenantId(),
                    input.entityId(),
                    null,
                    input.threshold(),
                    null,
                    "INVALID_NOT_COMPUTABLE",
                    "HALF_UP",
                    6);
        }
        BigDecimal ratio =
                new BigDecimal(input.numerator()).divide(denominator, 6, RoundingMode.HALF_UP);
        BigDecimal threshold = new BigDecimal(input.threshold());
        BigDecimal headroom = threshold.subtract(ratio).setScale(6, RoundingMode.HALF_UP);
        // Inclusive: ratio == threshold is compliant (headroom zero, non-negative).
        String status = headroom.signum() >= 0 ? "COMPLIANT" : "BREACH";
        return new Result(
                input.covenantId(),
                input.entityId(),
                ratio.toPlainString(),
                threshold.toPlainString(),
                headroom.toPlainString(),
                status,
                "HALF_UP",
                6);
    }
}
