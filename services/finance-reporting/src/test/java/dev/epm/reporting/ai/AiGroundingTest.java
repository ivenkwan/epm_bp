package dev.epm.reporting.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.epm.reporting.commentary.DeterministicCommentaryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 51 grounding evaluation: every route refuses safely without approval, certification, or with
 * the kill switch engaged; numeric claims compare exactly (value and scale) to cited strings;
 * deterministic commentary is complete with AI fully disabled. Mirrors
 * tests/ai-evaluation/grounding.
 */
class AiGroundingTest {

    private final AiControlPlane plane = new AiControlPlane();

    private static final AiControlPlane.Route COMMENTARY_ROUTE =
            new AiControlPlane.Route(
                    "commentary-draft",
                    1,
                    "env-ref",
                    "commentary-1.0.0",
                    true,
                    "ai-council",
                    List.of("kpi_snapshot_read"),
                    "eu",
                    false);

    private static final AiControlPlane.CertifiedSnapshot SNAPSHOT =
            new AiControlPlane.CertifiedSnapshot(
                    "snap-1", "kpi-cash", "2026-P01", "plan-bud-2027:r2", true, List.of("ENT-101"));

    @Test
    void disabledRoutesRefuseSafelyWithProvenance() {
        AiControlPlane.Gate noApproval =
                plane.admit(
                        new AiControlPlane.Invocation("commentary-draft", SNAPSHOT, "analyst@epm"),
                        List.of(
                                new AiControlPlane.Route(
                                        "commentary-draft",
                                        1,
                                        "ref",
                                        "v",
                                        false,
                                        "none",
                                        List.of(),
                                        "eu",
                                        false)),
                        new AiControlPlane.RouteState(true, null, null),
                        true);
        assertThat(noApproval.allowed()).isFalse();
        assertThat(noApproval.reason()).contains("signed approval");
        assertThat(noApproval.provenance().validation()).contains("REFUSED");

        AiControlPlane.Gate uncertified =
                plane.admit(
                        new AiControlPlane.Invocation(
                                "commentary-draft",
                                new AiControlPlane.CertifiedSnapshot(
                                        "snap-2", "kpi-cash", "2026-P01", "v", false, List.of()),
                                "analyst@epm"),
                        List.of(COMMENTARY_ROUTE),
                        new AiControlPlane.RouteState(true, null, null),
                        true);
        assertThat(uncertified.allowed()).isFalse();
        assertThat(uncertified.reason()).contains("uncertified snapshot");

        AiControlPlane.Gate unknown =
                plane.admit(
                        new AiControlPlane.Invocation("ghost", SNAPSHOT, "a"),
                        List.of(COMMENTARY_ROUTE),
                        new AiControlPlane.RouteState(true, null, null),
                        true);
        assertThat(unknown.allowed()).isFalse();
        assertThat(unknown.reason()).isEqualTo("unknown route");
    }

    @Test
    void numericClaimsCompareExactlyByValueAndScale() {
        assertThat(
                        plane.numericFidelity(
                                "Cash is 1500.00 against plan.",
                                List.of(new AiControlPlane.NumericClaim("cash", "1500.00"))))
                .isTrue();
        // Same value, different presented scale: invalid.
        assertThat(
                        plane.numericFidelity(
                                "Cash is 1500.0.",
                                List.of(new AiControlPlane.NumericClaim("cash", "1500.00"))))
                .isFalse();
        // Unsupported claim: invalid.
        assertThat(
                        plane.numericFidelity(
                                "Cash is 9999.99.",
                                List.of(new AiControlPlane.NumericClaim("cash", "1500.00"))))
                .isFalse();
        // Altered number vs cited evidence: invalid.
        assertThat(
                        plane.numericFidelity(
                                "Cash is 1501.00.",
                                List.of(new AiControlPlane.NumericClaim("cash", "1500.00"))))
                .isFalse();
    }

    @Test
    void reviewerPublishesOnlyIndependentAndValidDrafts() {
        assertThat(
                        plane.review(
                                        "d",
                                        true,
                                        "ai-route",
                                        "reviewer@epm",
                                        AiControlPlane.ReviewDecision.PUBLISH)
                                .published())
                .isTrue();
        assertThat(
                        plane.review(
                                        "d",
                                        true,
                                        "ai-route",
                                        "ai-route",
                                        AiControlPlane.ReviewDecision.PUBLISH)
                                .note())
                .contains("must differ");
        assertThat(
                        plane.review(
                                        "d",
                                        false,
                                        "ai-route",
                                        "reviewer@epm",
                                        AiControlPlane.ReviewDecision.PUBLISH)
                                .status())
                .isEqualTo("SUPPRESSED");
        assertThat(
                        plane.review(
                                        "d",
                                        true,
                                        "ai-route",
                                        "reviewer@epm",
                                        AiControlPlane.ReviewDecision.REJECT)
                                .published())
                .isFalse();
    }

    @Test
    void deterministicCommentaryIsCompleteWithAiDisabled() {
        DeterministicCommentaryService commentary = new DeterministicCommentaryService();
        DeterministicCommentaryService.Commentary text =
                commentary.comment(
                        new DeterministicCommentaryService.Metric(
                                "kpi-cash",
                                "2026-P01",
                                new BigDecimal("1500.00"),
                                "ed-10#kpi-cash"),
                        new DeterministicCommentaryService.Variance(
                                "kpi-cash",
                                new BigDecimal("100.00"),
                                new BigDecimal("0.071400"),
                                "var-1"),
                        new DeterministicCommentaryService.NarrativeRule(
                                new BigDecimal("0.05"),
                                "Management action is in progress on the adverse movement.",
                                "The movement is ahead of plan and monitored."));
        assertThat(text.text())
                .contains("kpi-cash")
                .contains("1500.00")
                .contains("100.00")
                .contains("ahead of plan");
        assertThat(text.evidenceRefs()).containsExactly("ed-10#kpi-cash", "var-1");
        // No AI dependency exists: construction never touched the control plane.
        assertThat(
                        plane.admit(
                                        new AiControlPlane.Invocation(
                                                "commentary-draft", SNAPSHOT, "a"),
                                        List.of(COMMENTARY_ROUTE),
                                        plane.disable("operator@epm", "drill"),
                                        true)
                                .allowed())
                .as("kill switch disables the route while commentary stands alone")
                .isFalse();
    }
}
