package dev.epm.core.certification;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reconciliation statements (Task 20): source, loaded, and aggregate totals compared under a
 * versioned tolerance. Exact matches, exact-tolerance boundaries, and over-tolerance blocks carry
 * complete lineage (load + rule version) and an explanation the preparer can act on.
 */
@ApplicationScoped
public class ReconciliationService {

    /** Outcome of one reconciliation. */
    public record Statement(
            String reconciliationId,
            UUID loadId,
            String ruleVersion,
            BigDecimal sourceTotal,
            BigDecimal loadedTotal,
            BigDecimal aggregateTotal,
            BigDecimal difference,
            BigDecimal tolerance,
            String status,
            String explanation) {}

    /**
     * Reconciles one load: difference = |loaded - source| evaluated against the tolerance with an
     * inclusive boundary (difference == tolerance still matches).
     */
    public Statement reconcile(
            String reconciliationId,
            UUID loadId,
            String ruleVersion,
            BigDecimal sourceTotal,
            BigDecimal loadedTotal,
            BigDecimal aggregateTotal,
            BigDecimal tolerance) {
        BigDecimal difference = loadedTotal.subtract(sourceTotal).abs();
        String status;
        String explanation;
        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            status = "MATCHED_EXACT";
            explanation = "loaded total equals the source total exactly";
        } else if (difference.compareTo(tolerance) <= 0) {
            status = "MATCHED_AT_EXACT_TOLERANCE";
            explanation =
                    "difference "
                            + difference.toPlainString()
                            + " is within the inclusive tolerance "
                            + tolerance.toPlainString();
        } else {
            status = "BLOCKED_OVER_TOLERANCE";
            explanation =
                    "unexplained difference "
                            + difference.toPlainString()
                            + " exceeds the tolerance "
                            + tolerance.toPlainString();
        }
        return new Statement(
                reconciliationId,
                loadId,
                ruleVersion,
                sourceTotal,
                loadedTotal,
                aggregateTotal,
                difference,
                tolerance,
                status,
                explanation);
    }
}
