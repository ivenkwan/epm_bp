package dev.epm.planning.versions;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 41 acceptance (AC-F-024-01–03): seeding happens exactly once with source lineage; empty
 * eligible selection is a no-data outcome, never an approved empty plan; final approval locks;
 * locked versions refuse edits (entitlement-checked); reopen requires nonblank reason/scope and
 * approved requests create a new revision while the prior approved version stays unchanged;
 * approved versions are reproducible; stale revisions are refused; decisions survive restart.
 */
class PlanVersionTest {

    private final PlanVersionService service = new PlanVersionService();

    private static final PlanVersionService.Entitlements ENTITLEMENTS =
            new PlanVersionService.Entitlements(
                    List.of("planner@epm", "editor@epm"),
                    List.of("planner@epm", "controller@epm"),
                    List.of("approver@epm", "controller@epm"));

    private static PlanVersionService.PlanVersion draft() {
        return new PlanVersionService.PlanVersion(
                "plan-bud-2027",
                PlanVersionService.PlanType.BUDGET,
                "FY2027 Budget",
                2027,
                PlanVersionService.PlanStatus.DRAFT,
                1,
                null,
                null,
                null,
                null,
                "planner@epm",
                List.of(),
                List.of(new PlanVersionService.Assumption("a1", "Volume +4%", "planner@epm")));
    }

    private static List<PlanVersionService.SourceCell> actuals() {
        return List.of(
                new PlanVersionService.SourceCell(
                        "ENT-101|ACC-REVENUE", "2027-P01", new BigDecimal("1000.00")),
                new PlanVersionService.SourceCell(
                        "ENT-101|ACC-REVENUE", "2027-P02", new BigDecimal("1100.00")));
    }

    @Test
    void seedingHappensExactlyOnceWithPerCellSourceLineage() {
        PlanVersionService.SeedResult first =
                service.seed(
                        draft(),
                        actuals(),
                        PlanVersionService.SeedSourceType.ACTUALS,
                        "actuals-certified-2026");
        assertThat(first.seeded()).isTrue();
        assertThat(first.seededCells()).hasSize(2);
        assertThat(first.seededCells().get(0).sourceCellRef())
                .isEqualTo("actuals-certified-2026/ENT-101|ACC-REVENUE/2027-P01");

        PlanVersionService.SeedResult second =
                service.seed(
                        first.plan(),
                        actuals(),
                        PlanVersionService.SeedSourceType.ACTUALS,
                        "actuals-certified-2026");
        assertThat(second.seeded()).isFalse();
        assertThat(second.note()).contains("exactly once");
    }

    @Test
    void emptyEligibleSelectionIsANoDataOutcomeNeverAnApprovedEmptyPlan() {
        PlanVersionService.SeedResult noData =
                service.seed(
                        draft(),
                        List.of(),
                        PlanVersionService.SeedSourceType.ACTUALS,
                        "actuals-certified-2026");
        assertThat(noData.seeded()).isFalse();
        assertThat(noData.note()).contains("no-data");
        assertThat(noData.plan().status()).isEqualTo(PlanVersionService.PlanStatus.DRAFT);
        assertThat(noData.plan().cells()).isEmpty();
        // An empty version cannot even be submitted for approval.
        PlanVersionService.SubmitResult refused =
                service.submit(noData.plan(), "planner@epm", ENTITLEMENTS);
        assertThat(refused.submitted()).isFalse();
        assertThat(refused.note()).contains("empty version cannot be submitted");
    }

    @Test
    void finalApprovalLocksAndRecordsAReproducibleContentHash() {
        PlanVersionService.PlanVersion seeded =
                service.seed(
                                draft(),
                                actuals(),
                                PlanVersionService.SeedSourceType.ACTUALS,
                                "actuals-certified-2026")
                        .plan();
        PlanVersionService.PlanVersion submitted =
                service.submit(seeded, "planner@epm", ENTITLEMENTS).plan();
        PlanVersionService.ApprovalResult approved =
                service.approve(submitted, "approver@epm", ENTITLEMENTS, "accepted");
        assertThat(approved.approved()).isTrue();
        assertThat(approved.plan().status()).isEqualTo(PlanVersionService.PlanStatus.LOCKED);
        assertThat(approved.plan().contentHash()).hasSize(64);
        // Reproducible: same cells + revision re-hash identically.
        PlanVersionService.ApprovalResult replay =
                service.approve(submitted, "controller@epm", ENTITLEMENTS, "replay");
        assertThat(replay.plan().contentHash()).isEqualTo(approved.plan().contentHash());
    }

    @Test
    void selfApprovalAndUnentitledApproversAreDenied() {
        PlanVersionService.PlanVersion seeded =
                service.seed(
                                draft(),
                                actuals(),
                                PlanVersionService.SeedSourceType.ACTUALS,
                                "actuals-certified-2026")
                        .plan();
        PlanVersionService.PlanVersion submitted =
                service.submit(seeded, "planner@epm", ENTITLEMENTS).plan();
        // Owner attempting self-approval.
        PlanVersionService.ApprovalResult self =
                service.approve(submitted, "planner@epm", ENTITLEMENTS, "self");
        assertThat(self.approved()).isFalse();
        assertThat(self.note()).contains("self-approval is denied");
        // Unentitled approver.
        PlanVersionService.ApprovalResult outsider =
                service.approve(submitted, "random@epm", ENTITLEMENTS, "nope");
        assertThat(outsider.approved()).isFalse();
        assertThat(outsider.note()).contains("not entitled");
    }

    @Test
    void lockedVersionsRefuseEditsEvenForEntitledEditors() {
        PlanVersionService.PlanVersion locked = lockedPlan();
        PlanVersionService.EditResult refused =
                service.editCell(
                        locked,
                        1,
                        "ENT-101|ACC-REVENUE",
                        "2027-P01",
                        new BigDecimal("999.00"),
                        "planner@epm",
                        ENTITLEMENTS);
        assertThat(refused.edited()).isFalse();
        assertThat(refused.note()).contains("LOCKED").contains("immutable");
        assertThat(refused.plan()).isEqualTo(locked);
    }

    @Test
    void unauthorizedEditorAndStaleRevisionAreRefused() {
        PlanVersionService.PlanVersion seeded =
                service.seed(
                                draft(),
                                actuals(),
                                PlanVersionService.SeedSourceType.ACTUALS,
                                "actuals-certified-2026")
                        .plan();
        PlanVersionService.EditResult outsider =
                service.editCell(
                        seeded,
                        1,
                        "ENT-101|ACC-REVENUE",
                        "2027-P01",
                        new BigDecimal("900.00"),
                        "random@epm",
                        ENTITLEMENTS);
        assertThat(outsider.edited()).isFalse();
        assertThat(outsider.note()).contains("not entitled");

        PlanVersionService.EditResult stale =
                service.editCell(
                        seeded,
                        7,
                        "ENT-101|ACC-REVENUE",
                        "2027-P01",
                        new BigDecimal("900.00"),
                        "planner@epm",
                        ENTITLEMENTS);
        assertThat(stale.edited()).isFalse();
        assertThat(stale.note()).contains("stale state");

        PlanVersionService.EditResult fresh =
                service.editCell(
                        seeded,
                        1,
                        "ENT-101|ACC-REVENUE",
                        "2027-P01",
                        new BigDecimal("900.00"),
                        "planner@epm",
                        ENTITLEMENTS);
        assertThat(fresh.edited()).isTrue();
    }

    @Test
    void blankReasonScopeAndEntitlementRefuseReopenRequests() {
        PlanVersionService.PlanVersion locked = lockedPlan();
        assertThat(
                        service.requestReopen(
                                        locked,
                                        new PlanVersionService.ReopenRequestInput(
                                                "rq-1", "planner@epm", "  ", "EU revenue"),
                                        ENTITLEMENTS)
                                .note())
                .contains("reason must be nonblank");
        assertThat(
                        service.requestReopen(
                                        locked,
                                        new PlanVersionService.ReopenRequestInput(
                                                "rq-2", "planner@epm", "Assumption change", ""),
                                        ENTITLEMENTS)
                                .note())
                .contains("scope must be nonblank");
        assertThat(
                        service.requestReopen(
                                        locked,
                                        new PlanVersionService.ReopenRequestInput(
                                                "rq-3", "random@epm", "Reason", "Scope"),
                                        ENTITLEMENTS)
                                .note())
                .contains("not entitled");
        assertThat(
                        service.requestReopen(
                                        locked,
                                        new PlanVersionService.ReopenRequestInput(
                                                "rq-4",
                                                "controller@epm",
                                                "FX basis corrected after close",
                                                "EU revenue lines"),
                                        ENTITLEMENTS)
                                .accepted())
                .isTrue();
    }

    @Test
    void approvedReopenCreatesNewRevisionLineageAndPreservesThePriorVersion() {
        PlanVersionService.PlanVersion locked = lockedPlan();
        BigDecimal priorValue = locked.cells().get(0).value();

        PlanVersionService.ReopenOutcome outcome = service.approveReopen(locked, "controller@epm");
        assertThat(outcome.reopened()).isTrue();
        assertThat(outcome.newDraftRevision().revision()).isEqualTo(2);
        assertThat(outcome.newDraftRevision().status())
                .isEqualTo(PlanVersionService.PlanStatus.DRAFT);
        assertThat(outcome.newDraftRevision().parentPlanId()).isEqualTo("plan-bud-2027");
        assertThat(outcome.newDraftRevision().seedSourceType())
                .isEqualTo(PlanVersionService.SeedSourceType.PRIOR_PLAN);
        // Carried cells cite the prior plan revision as their lineage.
        assertThat(outcome.newDraftRevision().cells().get(0).sourceCellRef())
                .isEqualTo("prior-plan:plan-bud-2027:1");

        // The previously approved version is unchanged and recorded as SUPERSEDED.
        assertThat(outcome.priorApprovedVersion().status())
                .isEqualTo(PlanVersionService.PlanStatus.SUPERSEDED);
        assertThat(outcome.priorApprovedVersion().cells().get(0).value())
                .isEqualByComparingTo(priorValue);
        assertThat(outcome.priorApprovedVersion().contentHash()).isEqualTo(locked.contentHash());

        // Rework the new revision, then submit and relock.
        PlanVersionService.PlanVersion edited =
                service.editCell(
                                outcome.newDraftRevision(),
                                2,
                                "ENT-101|ACC-REVENUE",
                                "2027-P01",
                                new BigDecimal("1200.00"),
                                "planner@epm",
                                ENTITLEMENTS)
                        .plan();
        PlanVersionService.PlanVersion resubmitted =
                service.submit(edited, "planner@epm", ENTITLEMENTS).plan();
        PlanVersionService.ApprovalResult relocked =
                service.approve(resubmitted, "approver@epm", ENTITLEMENTS, "relocked");
        assertThat(relocked.approved()).isTrue();
        assertThat(relocked.plan().status()).isEqualTo(PlanVersionService.PlanStatus.LOCKED);
        assertThat(relocked.plan().revision()).isEqualTo(2);
        assertThat(relocked.plan().contentHash()).isNotEqualTo(locked.contentHash());
    }

    @Test
    void comparisonDiffsCellsBetweenRevisions() {
        PlanVersionService.PlanVersion locked = lockedPlan();
        PlanVersionService.ReopenOutcome reopened = service.approveReopen(locked, "controller@epm");
        PlanVersionService.PlanVersion edited =
                service.editCell(
                                reopened.newDraftRevision(),
                                2,
                                "ENT-101|ACC-REVENUE",
                                "2027-P01",
                                new BigDecimal("1500.00"),
                                "planner@epm",
                                ENTITLEMENTS)
                        .plan();
        PlanVersionService.CompareResult diff = service.compare(locked, edited);
        assertThat(diff.differences()).hasSize(1);
        assertThat(diff.differences().get(0).left()).isEqualByComparingTo("1000.00");
        assertThat(diff.differences().get(0).right()).isEqualByComparingTo("1500.00");
    }

    @Test
    void decisionsSurviveRestartFromPersistedStateAlone() {
        // A brand-new service instance continues from the version record only — no
        // in-memory authority exists.
        PlanVersionService.PlanVersion submittedState =
                service.submit(
                                service.seed(
                                                draft(),
                                                actuals(),
                                                PlanVersionService.SeedSourceType.ACTUALS,
                                                "actuals-certified-2026")
                                        .plan(),
                                "planner@epm",
                                ENTITLEMENTS)
                        .plan();
        PlanVersionService fresh = new PlanVersionService();
        PlanVersionService.ApprovalResult approved =
                fresh.approve(submittedState, "approver@epm", ENTITLEMENTS, "after restart");
        assertThat(approved.approved()).isTrue();
        assertThat(PlanVersionService.lockProvenance(approved.plan()))
                .contains("locked@" + approved.plan().contentHash());
    }

    private PlanVersionService.PlanVersion lockedPlan() {
        PlanVersionService.PlanVersion seeded =
                service.seed(
                                draft(),
                                actuals(),
                                PlanVersionService.SeedSourceType.ACTUALS,
                                "actuals-certified-2026")
                        .plan();
        PlanVersionService.PlanVersion submitted =
                service.submit(seeded, "planner@epm", ENTITLEMENTS).plan();
        return service.approve(submitted, "approver@epm", ENTITLEMENTS, "locked").plan();
    }
}
