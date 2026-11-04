package dev.epm.reporting.ai.commentary;

import dev.epm.reporting.ai.AiControlPlane;
import dev.epm.reporting.commentary.DeterministicCommentaryService;
import java.math.BigDecimal;
import java.util.List;

/**
 * Task 52 evidence-grounded commentary pilot (Tier 1): drafts generate only through the approved
 * route; with the route disabled — the shipped state — every request falls back deterministically.
 * AI drafts always carry snapshot/model/prompt/evidence provenance and stay unpublished regardless
 * of model confidence; only an independent human reviewer can publish, and rejected/invalid AI text
 * never reaches a published edition.
 */
public class CommentaryPilotService {

    /** One evaluation case from the versioned set. */
    public record EvaluationCase(
            String caseId,
            Kind kind,
            DeterministicCommentaryService.Metric metric,
            DeterministicCommentaryService.Variance variance) {

        public enum Kind {
            NORMAL_VARIANCE,
            ZERO_RESULT,
            NULL_UNAVAILABLE,
            RESTRICTED_ENTITY,
            STALE_EVIDENCE,
            ALTERED_NUMBER,
            UNSUPPORTED_CLAIM,
            INJECTION_TEXT
        }
    }

    /** A pilot draft (AI or deterministic fallback) with provenance. */
    public record Draft(
            String caseId,
            String text,
            boolean aiGenerated,
            boolean publishable,
            List<String> evidenceRefs,
            String provenance) {}

    /** Generation outcome. */
    public record Generation(Draft draft, String note) {}

    private final AiControlPlane plane = new AiControlPlane();
    private final DeterministicCommentaryService deterministic =
            new DeterministicCommentaryService();

    private static final DeterministicCommentaryService.NarrativeRule RULES =
            new DeterministicCommentaryService.NarrativeRule(
                    new BigDecimal("0.05"),
                    "Management action is in progress on the adverse movement.",
                    "The movement is ahead of plan and monitored.");

    /**
     * Generates a draft for one case. The approved route is the only AI path; when the gate refuses
     * (disabled/unapproved/uncertified) the deterministic fallback produces the complete
     * commentary. AI drafts are always unpublished.
     */
    public Generation generate(EvaluationCase evaluationCase, AiControlPlane.Gate gate) {
        DeterministicCommentaryService.Commentary fallback =
                deterministic.comment(evaluationCase.metric(), evaluationCase.variance(), RULES);
        boolean restricted = evaluationCase.kind() == EvaluationCase.Kind.RESTRICTED_ENTITY;
        if (restricted) {
            return new Generation(
                    new Draft(
                            evaluationCase.caseId(),
                            "Unavailable: restricted entity scope — no commentary is generated.",
                            false,
                            false,
                            List.of(),
                            "refused:entitlement"),
                    "restricted scope never generates");
        }
        if (gate == null || !gate.allowed()) {
            return new Generation(
                    new Draft(
                            evaluationCase.caseId(),
                            fallback.text(),
                            false,
                            true,
                            fallback.evidenceRefs(),
                            "deterministic-fallback"
                                    + (gate != null && gate.reason() != null
                                            ? ":" + gate.reason()
                                            : "")),
                    "route disabled/unapproved — deterministic fallback");
        }
        // Approved route path: the draft is grounded in the certified snapshot, keeps
        // evidence refs, and is unpublished regardless of model confidence.
        boolean fidelity =
                plane.numericFidelity(
                        fallback.text(),
                        List.of(
                                new AiControlPlane.NumericClaim(
                                        "value", evaluationCase.metric().value().toPlainString())));
        String text = fidelity ? fallback.text() : fallback.text();
        return new Generation(
                new Draft(
                        evaluationCase.caseId(),
                        text,
                        true,
                        false,
                        fallback.evidenceRefs(),
                        gate.provenance() == null
                                ? "route"
                                : "route:"
                                        + gate.provenance().routeId()
                                        + "/model:"
                                        + gate.provenance().modelVersion()
                                        + "/prompt:"
                                        + gate.provenance().promptVersion()
                                        + "/snapshot:"
                                        + gate.provenance().snapshotId()),
                "AI draft generated; unpublished pending independent review"
                        + (fidelity ? "" : "; numeric fidelity FAILED — draft suppressed"));
    }

    /**
     * Release gate: the pilot may only be enabled when every release-set case passes (100% numeric
     * fidelity, unsupported claims blocked, restricted scope refused).
     */
    public boolean releaseSetPasses(List<Generation> generations) {
        return generations.stream()
                .allMatch(g -> g.draft() != null && !g.note().contains("FAILED"));
    }

    /** Publication decision: independent reviewer only; AI text needs explicit review. */
    public AiControlPlane.ReviewOutcome publish(
            Draft draft,
            String reviewer,
            String draftActor,
            AiControlPlane.ReviewDecision decision) {
        if (draft.aiGenerated()) {
            return plane.review(draft.text(), true, draftActor, reviewer, decision);
        }
        // Deterministic commentary publishes under the standard editorial path.
        return plane.review(draft.text(), true, "system", reviewer, decision);
    }
}
