package dev.epm.reporting.ai.commentary;

import static org.assertj.core.api.Assertions.assertThat;

import dev.epm.reporting.ai.AiControlPlane;
import dev.epm.reporting.commentary.DeterministicCommentaryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 52 pilot evaluation (route DISABLED in this release): the versioned evaluation set covers
 * normal variances, zero/null/unavailable, restricted entities, stale evidence, altered numbers,
 * unsupported claims, and injection text. Every case must produce a governed draft or a safe
 * refusal with the deterministic fallback intact; AI drafts stay unpublished regardless of model
 * confidence; only independent review publishes; any release-set failure keeps the route disabled.
 */
class CommentaryPilotTest {

    private final CommentaryPilotService pilot = new CommentaryPilotService();

    private static CommentaryPilotService.EvaluationCase caseOf(
            String id, CommentaryPilotService.EvaluationCase.Kind kind, String value) {
        return new CommentaryPilotService.EvaluationCase(
                id,
                kind,
                new DeterministicCommentaryService.Metric(
                        "kpi-cash", "2026-P01", new BigDecimal(value), "ed-10#kpi-cash"),
                new DeterministicCommentaryService.Variance(
                        "kpi-cash", new BigDecimal("100.00"), new BigDecimal("0.071400"), "var-1"));
    }

    @Test
    void disabledRouteFallsBackDeterministicallyForEveryReleaseSetCase() {
        List<CommentaryPilotService.EvaluationCase> releaseSet =
                List.of(
                        caseOf(
                                "c1",
                                CommentaryPilotService.EvaluationCase.Kind.NORMAL_VARIANCE,
                                "1500.00"),
                        caseOf(
                                "c2",
                                CommentaryPilotService.EvaluationCase.Kind.ZERO_RESULT,
                                "0.00"),
                        caseOf(
                                "c3",
                                CommentaryPilotService.EvaluationCase.Kind.NULL_UNAVAILABLE,
                                "0.00"),
                        caseOf(
                                "c4",
                                CommentaryPilotService.EvaluationCase.Kind.STALE_EVIDENCE,
                                "1500.00"),
                        caseOf(
                                "c5",
                                CommentaryPilotService.EvaluationCase.Kind.ALTERED_NUMBER,
                                "1501.00"),
                        caseOf(
                                "c6",
                                CommentaryPilotService.EvaluationCase.Kind.UNSUPPORTED_CLAIM,
                                "1500.00"),
                        caseOf(
                                "c7",
                                CommentaryPilotService.EvaluationCase.Kind.INJECTION_TEXT,
                                "1500.00"));
        // The gate refuses: the registry ships disabled.
        AiControlPlane.Gate refused =
                new AiControlPlane.Gate(false, "route lacks signed approval", null);
        List<CommentaryPilotService.Generation> generations =
                releaseSet.stream().map(c -> pilot.generate(c, refused)).toList();
        assertThat(pilot.releaseSetPasses(generations)).isTrue();
        assertThat(generations)
                .allSatisfy(
                        g -> {
                            assertThat(g.draft().aiGenerated()).isFalse();
                            assertThat(g.draft().provenance()).contains("deterministic-fallback");
                        });
        // Restricted entity never generates commentary at all.
        CommentaryPilotService.Generation restricted =
                pilot.generate(
                        caseOf(
                                "c4b",
                                CommentaryPilotService.EvaluationCase.Kind.RESTRICTED_ENTITY,
                                "1500.00"),
                        refused);
        assertThat(restricted.draft().publishable()).isFalse();
        assertThat(restricted.note()).contains("never generates");
    }

    @Test
    void approvedRouteDraftsCarryProvenanceAndStayUnpublished() {
        AiControlPlane.Provenance provenance =
                new AiControlPlane.Provenance(
                        "commentary-draft",
                        "model-x",
                        "commentary-1.0.0",
                        "snap-1",
                        "admitted",
                        "analyst@epm");
        AiControlPlane.Gate allowed = new AiControlPlane.Gate(true, null, provenance);
        CommentaryPilotService.Generation draft =
                pilot.generate(
                        caseOf(
                                "c1",
                                CommentaryPilotService.EvaluationCase.Kind.NORMAL_VARIANCE,
                                "1500.00"),
                        allowed);
        assertThat(draft.draft().aiGenerated()).isTrue();
        assertThat(draft.draft().publishable())
                .as("AI drafts are unpublished regardless of model confidence")
                .isFalse();
        assertThat(draft.draft().provenance())
                .contains("route:commentary-draft")
                .contains("model:model-x")
                .contains("prompt:commentary-1.0.0")
                .contains("snapshot:snap-1");
    }

    @Test
    void publicationRequiresIndependentReviewAndRejectionNeverPublishes() {
        CommentaryPilotService.Generation draft =
                pilot.generate(
                        caseOf(
                                "c1",
                                CommentaryPilotService.EvaluationCase.Kind.NORMAL_VARIANCE,
                                "1500.00"),
                        new AiControlPlane.Gate(false, "disabled", null));
        AiControlPlane.ReviewOutcome published =
                pilot.publish(
                        draft.draft(),
                        "reviewer@epm",
                        "analyst@epm",
                        AiControlPlane.ReviewDecision.PUBLISH);
        assertThat(published.published()).isTrue();

        AiControlPlane.ReviewOutcome rejected =
                pilot.publish(
                        draft.draft(),
                        "reviewer@epm",
                        "analyst@epm",
                        AiControlPlane.ReviewDecision.REJECT);
        assertThat(rejected.published()).isFalse();
        // Independence applies to AI drafts: the draft actor cannot self-publish.
        CommentaryPilotService.Draft aiDraft =
                new CommentaryPilotService.Draft("c-ai", "text", true, false, List.of(), "route");
        assertThat(
                        pilot.publish(
                                        aiDraft,
                                        "analyst@epm",
                                        "analyst@epm",
                                        AiControlPlane.ReviewDecision.PUBLISH)
                                .note())
                .contains("must differ");
    }

    @Test
    void releaseSetFailureKeepsTheRouteDisabled() {
        // A generation with a failed-fidelity note must fail the release gate.
        CommentaryPilotService.Generation bad =
                new CommentaryPilotService.Generation(
                        new CommentaryPilotService.Draft(
                                "c-bad", "text", true, false, List.of(), "route"),
                        "AI draft generated; numeric fidelity FAILED — draft suppressed");
        assertThat(pilot.releaseSetPasses(List.of(bad))).isFalse();
    }
}
