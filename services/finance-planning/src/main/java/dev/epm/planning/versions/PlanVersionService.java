package dev.epm.planning.versions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Task 41 plan-version governance: immutable Budget/Forecast versions with seed rules (exactly
 * once, with per-cell source lineage), independent approval whose final act locks the version, and
 * controlled reopening — a nonblank-reason, scoped, approved request that creates a new revision
 * while the previously approved version stays unchanged. Empty eligible seeding returns a no-data
 * outcome and can never produce an approved empty plan. Edits are entitlement-checked and refuse
 * stale revisions (ETag discipline); timers and automation never approve, lock, or reopen.
 */
public class PlanVersionService {

    public enum PlanType {
        BUDGET,
        FORECAST
    }

    public enum PlanStatus {
        DRAFT,
        SUBMITTED,
        APPROVED,
        LOCKED,
        SUPERSEDED
    }

    public enum SeedSourceType {
        ACTUALS,
        PRIOR_PLAN,
        MANUAL
    }

    /** One seeded/edited cell with its source lineage. */
    public record PlanCell(
            String dimensionKey, String period, BigDecimal value, String sourceCellRef) {}

    /** A governed assumption attached to a version. */
    public record Assumption(String assumptionId, String body, String owner) {}

    /** Who may act on a plan version (entitlements are server-owned). */
    public record Entitlements(
            List<String> editors, List<String> reopenRequesters, List<String> approvers) {}

    /** The immutable plan version state (status transitions create new records). */
    public record PlanVersion(
            String planId,
            PlanType planType,
            String name,
            int fiscalYear,
            PlanStatus status,
            int revision,
            String parentPlanId,
            SeedSourceType seedSourceType,
            String seedSourceRef,
            String contentHash,
            String owner,
            List<PlanCell> cells,
            List<Assumption> assumptions) {}

    /** Seeding outcome: seeded plan, or a no-data/refused outcome. */
    public record SeedResult(
            boolean seeded, PlanVersion plan, String note, List<PlanCell> seededCells) {
        static SeedResult refused(PlanVersion plan, String note) {
            return new SeedResult(false, plan, note, List.of());
        }
    }

    /** Source cells offered for seeding (from certified actuals or a prior plan). */
    public record SourceCell(String dimensionKey, String period, BigDecimal value) {}

    /** Submission outcome. */
    public record SubmitResult(boolean submitted, PlanVersion plan, String note) {}

    /** Approval outcome; final approval locks. */
    public record ApprovalResult(
            boolean approved, PlanVersion plan, String decidedBy, String note) {}

    /** Cell edit outcome (entitlement + staleness checked). */
    public record EditResult(boolean edited, PlanVersion plan, String note) {}

    /** Comparison of two versions at cell grain. */
    public record CellDiff(String dimensionKey, String period, BigDecimal left, BigDecimal right) {}

    /** Comparison outcome. */
    public record CompareResult(
            String leftPlanId, String rightPlanId, List<CellDiff> differences) {}

    /** A reopen request (nonblank reason and scope required). */
    public record ReopenRequestInput(
            String requestId, String requestedBy, String reason, String scope) {}

    /** Reopen request validation outcome. */
    public record ReopenRequestResult(boolean accepted, String requestId, String note) {}

    /** Approved reopen outcome: a new revision lineage; prior version untouched. */
    public record ReopenOutcome(
            boolean reopened,
            PlanVersion newDraftRevision,
            PlanVersion priorApprovedVersion,
            String note) {}

    /**
     * Seeds a DRAFT plan exactly once from eligible source cells. Every seeded cell records its
     * source lineage. An empty eligible selection is a no-data outcome — the plan stays an
     * unapproved draft, never an approved empty plan.
     */
    public SeedResult seed(
            PlanVersion draft,
            List<SourceCell> eligibleSourceCells,
            SeedSourceType sourceType,
            String sourceRef) {
        if (draft.status() != PlanStatus.DRAFT) {
            return SeedResult.refused(draft, "only a DRAFT version can be seeded");
        }
        if (draft.cells() != null && !draft.cells().isEmpty()) {
            return SeedResult.refused(
                    draft, "version already seeded — seeding happens exactly once");
        }
        if (eligibleSourceCells == null || eligibleSourceCells.isEmpty()) {
            return SeedResult.refused(
                    draft,
                    "no eligible source selection — no-data outcome; an empty plan is never"
                            + " approved");
        }
        List<PlanCell> seeded =
                eligibleSourceCells.stream()
                        .map(
                                c ->
                                        new PlanCell(
                                                c.dimensionKey(),
                                                c.period(),
                                                c.value(),
                                                sourceRef
                                                        + "/"
                                                        + c.dimensionKey()
                                                        + "/"
                                                        + c.period()))
                        .toList();
        PlanVersion seededPlan = withCells(draft, seeded, sourceType, sourceRef, PlanStatus.DRAFT);
        return new SeedResult(
                true,
                seededPlan,
                "seeded " + seeded.size() + " cells from " + sourceType + " " + sourceRef,
                seeded);
    }

    /** DRAFT → SUBMITTED (entitlement-checked). */
    public SubmitResult submit(PlanVersion plan, String actor, Entitlements entitlements) {
        if (!entitlements.editors().contains(actor)) {
            return new SubmitResult(false, plan, "actor not entitled to submit this version");
        }
        if (plan.status() != PlanStatus.DRAFT) {
            return new SubmitResult(false, plan, "only a DRAFT version can be submitted");
        }
        if (plan.cells().isEmpty()) {
            return new SubmitResult(
                    false, plan, "an empty version cannot be submitted for approval");
        }
        return new SubmitResult(
                true,
                new PlanVersion(
                        plan.planId(),
                        plan.planType(),
                        plan.name(),
                        plan.fiscalYear(),
                        PlanStatus.SUBMITTED,
                        plan.revision(),
                        plan.parentPlanId(),
                        plan.seedSourceType(),
                        plan.seedSourceRef(),
                        plan.contentHash(),
                        plan.owner(),
                        plan.cells(),
                        plan.assumptions()),
                "submitted");
    }

    /**
     * Independent approval of a SUBMITTED version: the final approval locks it. The owner can never
     * approve their own version; approvers are governed by entitlement.
     */
    public ApprovalResult approve(
            PlanVersion plan, String approver, Entitlements entitlements, String comment) {
        if (approver.equals(plan.owner())) {
            return new ApprovalResult(
                    false, plan, approver, "self-approval is denied — approval is independent");
        }
        if (!entitlements.approvers().contains(approver)) {
            return new ApprovalResult(false, plan, approver, "actor not entitled to approve");
        }
        if (plan.status() != PlanStatus.SUBMITTED) {
            return new ApprovalResult(
                    false, plan, approver, "only a SUBMITTED version can be approved");
        }
        PlanStatus next = PlanStatus.LOCKED;
        PlanVersion approved =
                new PlanVersion(
                        plan.planId(),
                        plan.planType(),
                        plan.name(),
                        plan.fiscalYear(),
                        next,
                        plan.revision(),
                        plan.parentPlanId(),
                        plan.seedSourceType(),
                        plan.seedSourceRef(),
                        contentHash(plan),
                        plan.owner(),
                        plan.cells(),
                        plan.assumptions());
        return new ApprovalResult(
                true, approved, approver, "approved and locked; content hash recorded");
    }

    /**
     * Edits one cell. Only DRAFT versions are editable; the actor must be an entitled editor; the
     * expected revision must match (stale ETag refused).
     */
    public EditResult editCell(
            PlanVersion plan,
            int expectedRevision,
            String dimensionKey,
            String period,
            BigDecimal newValue,
            String actor,
            Entitlements entitlements) {
        if (!entitlements.editors().contains(actor)) {
            return new EditResult(false, plan, "actor not entitled to edit this version");
        }
        if (plan.status() == PlanStatus.LOCKED
                || plan.status() == PlanStatus.APPROVED
                || plan.status() == PlanStatus.SUBMITTED) {
            return new EditResult(
                    false,
                    plan,
                    "version is " + plan.status() + " — locked versions are immutable");
        }
        if (plan.revision() != expectedRevision) {
            return new EditResult(
                    false,
                    plan,
                    "stale state: expected revision "
                            + expectedRevision
                            + " but current is "
                            + plan.revision()
                            + " — reload and retry");
        }
        List<PlanCell> cells = new ArrayList<>(plan.cells());
        cells.removeIf(c -> c.dimensionKey().equals(dimensionKey) && c.period().equals(period));
        cells.add(new PlanCell(dimensionKey, period, newValue, "manual:" + actor));
        cells.sort(Comparator.comparing(PlanCell::dimensionKey).thenComparing(PlanCell::period));
        PlanVersion edited =
                new PlanVersion(
                        plan.planId(),
                        plan.planType(),
                        plan.name(),
                        plan.fiscalYear(),
                        plan.status(),
                        plan.revision(),
                        plan.parentPlanId(),
                        plan.seedSourceType(),
                        plan.seedSourceRef(),
                        null,
                        plan.owner(),
                        List.copyOf(cells),
                        plan.assumptions());
        return new EditResult(true, edited, "cell updated");
    }

    /** Cell-by-cell comparison of two versions (shared cells plus one-sided). */
    public CompareResult compare(PlanVersion left, PlanVersion right) {
        Map<String, PlanCell> rightByKey = new LinkedHashMap<>();
        right.cells().forEach(c -> rightByKey.put(cellKey(c), c));
        Map<String, PlanCell> leftByKey = new LinkedHashMap<>();
        left.cells().forEach(c -> leftByKey.put(cellKey(c), c));
        List<CellDiff> diffs = new ArrayList<>();
        leftByKey.keySet().stream()
                .sorted()
                .forEach(
                        key -> {
                            PlanCell l = leftByKey.get(key);
                            PlanCell r = rightByKey.get(key);
                            if (r == null || l.value().compareTo(r.value()) != 0) {
                                diffs.add(
                                        new CellDiff(
                                                l.dimensionKey(),
                                                l.period(),
                                                l.value(),
                                                r == null ? null : r.value()));
                            }
                        });
        rightByKey.keySet().stream()
                .filter(key -> !leftByKey.containsKey(key))
                .sorted()
                .forEach(
                        key -> {
                            PlanCell r = rightByKey.get(key);
                            diffs.add(new CellDiff(r.dimensionKey(), r.period(), null, r.value()));
                        });
        return new CompareResult(left.planId(), right.planId(), diffs);
    }

    /**
     * Validates a reopen request against a LOCKED version: entitlement, nonblank reason, nonblank
     * scope. Requests are recorded, never auto-applied.
     */
    public ReopenRequestResult requestReopen(
            PlanVersion locked, ReopenRequestInput input, Entitlements entitlements) {
        if (locked.status() != PlanStatus.LOCKED) {
            return new ReopenRequestResult(
                    false, input.requestId(), "only a LOCKED version can be reopened");
        }
        if (!entitlements.reopenRequesters().contains(input.requestedBy())) {
            return new ReopenRequestResult(
                    false, input.requestId(), "actor not entitled to request reopening");
        }
        if (input.reason() == null || input.reason().isBlank()) {
            return new ReopenRequestResult(
                    false, input.requestId(), "reopen reason must be nonblank");
        }
        if (input.scope() == null || input.scope().isBlank()) {
            return new ReopenRequestResult(
                    false, input.requestId(), "reopen scope must be nonblank");
        }
        return new ReopenRequestResult(
                true, input.requestId(), "reopen request recorded; awaiting approval");
    }

    /**
     * Approves a reopen request: creates a NEW revision (revision + 1) in DRAFT seeded from the
     * prior plan (lineage preserved via parent linkage); the previously approved version is
     * returned unchanged — it is never edited in place.
     */
    public ReopenOutcome approveReopen(PlanVersion locked, String decidedBy) {
        if (locked.status() != PlanStatus.LOCKED) {
            return new ReopenOutcome(false, locked, locked, "only a LOCKED version can reopen");
        }
        PlanVersion priorAsSuperseded =
                new PlanVersion(
                        locked.planId(),
                        locked.planType(),
                        locked.name(),
                        locked.fiscalYear(),
                        PlanStatus.SUPERSEDED,
                        locked.revision(),
                        locked.parentPlanId(),
                        locked.seedSourceType(),
                        locked.seedSourceRef(),
                        locked.contentHash(),
                        locked.owner(),
                        locked.cells(),
                        locked.assumptions());
        List<PlanCell> carried =
                locked.cells().stream()
                        .map(
                                c ->
                                        new PlanCell(
                                                c.dimensionKey(),
                                                c.period(),
                                                c.value(),
                                                "prior-plan:"
                                                        + locked.planId()
                                                        + ":"
                                                        + locked.revision()))
                        .toList();
        PlanVersion newDraft =
                new PlanVersion(
                        locked.planId(),
                        locked.planType(),
                        locked.name() + " (reopen r" + (locked.revision() + 1) + ")",
                        locked.fiscalYear(),
                        PlanStatus.DRAFT,
                        locked.revision() + 1,
                        locked.planId(),
                        SeedSourceType.PRIOR_PLAN,
                        locked.planId() + ":r" + locked.revision(),
                        null,
                        locked.owner(),
                        carried,
                        locked.assumptions());
        return new ReopenOutcome(
                true,
                newDraft,
                priorAsSuperseded,
                "reopened by " + decidedBy + "; prior approved version preserved unchanged");
    }

    private static PlanVersion withCells(
            PlanVersion draft,
            List<PlanCell> cells,
            SeedSourceType sourceType,
            String sourceRef,
            PlanStatus status) {
        List<PlanCell> sorted =
                cells.stream()
                        .sorted(
                                Comparator.comparing(PlanCell::dimensionKey)
                                        .thenComparing(PlanCell::period))
                        .toList();
        return new PlanVersion(
                draft.planId(),
                draft.planType(),
                draft.name(),
                draft.fiscalYear(),
                status,
                draft.revision(),
                draft.parentPlanId(),
                sourceType,
                sourceRef,
                null,
                draft.owner(),
                sorted,
                draft.assumptions());
    }

    private static String cellKey(PlanCell cell) {
        return cell.dimensionKey() + "@" + cell.period();
    }

    /** Deterministic hash over sorted cell values — reproducible approved versions. */
    private static String contentHash(PlanVersion plan) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(plan.planId()).append('|').append(plan.revision()).append('|');
        plan.cells().stream()
                .sorted(
                        Comparator.comparing(PlanCell::dimensionKey)
                                .thenComparing(PlanCell::period))
                .forEach(
                        c -> {
                            canonical
                                    .append(c.dimensionKey())
                                    .append('@')
                                    .append(c.period())
                                    .append('=')
                                    .append(c.value().stripTrailingZeros().toPlainString())
                                    .append(';');
                        });
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Optional helper for restart tests: any instance reproduces decisions from state. */
    public static Optional<String> lockProvenance(PlanVersion plan) {
        return plan.status() == PlanStatus.LOCKED
                ? Optional.of("locked@" + plan.contentHash())
                : Optional.empty();
    }
}
