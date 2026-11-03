package dev.epm.core.liquiditycontext;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/**
 * Optional stock/unit liquidity context (Task 36, F-022). Strictly an optional READ model over
 * contracted, certified submissions: it never infers liquidity or cash value, never turns a missing
 * quantity into zero, and never changes the certified cash position. Feature-disabled by default
 * until a business owner signs its activation; a signed deferral leaves this dormant with zero
 * effect on mandatory R2 acceptance.
 */
@ApplicationScoped
public class StockUnitContextService {

    /** One submitted stock/unit line. */
    public record StockLine(
            String itemId,
            String quantity,
            String ageingBucket,
            String classification,
            String movementRef,
            String optionalRemark,
            String sourceRevision,
            java.time.OffsetDateTime sourceFreshness) {}

    /** Rendered context row: zero stays zero; blank remarks stay blank. */
    public record ContextRow(
            String itemId,
            String quantity,
            Optional<String> ageingBucket,
            Optional<String> classification,
            Optional<String> optionalRemark,
            String sourceRevision,
            java.time.OffsetDateTime sourceFreshness) {}

    private volatile boolean enabled = false;

    /** Activation is an explicit, owner-recorded act (never a config default). */
    public void enable() {
        this.enabled = true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Builds the context rows for certified submissions. A null quantity is a validation quarantine
     * upstream (Task 19 NULL_AMOUNT) — it never renders here as zero; a submitted "0" renders as
     * "0" exactly.
     */
    public List<ContextRow> contextFor(List<StockLine> certifiedLines) {
        if (!enabled) {
            throw new IllegalStateException(
                    "stock/unit context is disabled; a named business owner must sign its"
                            + " activation");
        }
        return certifiedLines.stream()
                .map(
                        line ->
                                new ContextRow(
                                        line.itemId(),
                                        line.quantity(),
                                        Optional.ofNullable(emptyToNull(line.ageingBucket())),
                                        Optional.ofNullable(emptyToNull(line.classification())),
                                        Optional.ofNullable(emptyToNull(line.optionalRemark())),
                                        line.sourceRevision(),
                                        line.sourceFreshness()))
                .toList();
    }

    /** Detail drill: returns the row only when the requester's scope covers the entity. */
    public Optional<ContextRow> detail(
            List<ContextRow> rows, String itemId, boolean requesterScopedToEntity) {
        if (!requesterScopedToEntity) {
            // Denied scope: empty result, no detail leakage.
            return Optional.empty();
        }
        return rows.stream().filter(row -> row.itemId().equals(itemId)).findFirst();
    }

    /** Reconciliation: Σ quantities must tie to the submitted detail exactly. */
    public boolean reconcilesToSubmitted(List<ContextRow> rows, List<StockLine> submitted) {
        if (rows.size() != submitted.size()) {
            return false;
        }
        var expected = submitted.stream().map(line -> line.quantity()).sorted().toList();
        var actual = rows.stream().map(row -> row.quantity()).sorted().toList();
        return actual.equals(expected);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
