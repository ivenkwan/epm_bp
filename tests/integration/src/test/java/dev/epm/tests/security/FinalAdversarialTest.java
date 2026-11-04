package dev.epm.tests.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.epm.planning.eliminations.EliminationService;
import dev.epm.reporting.ai.AiControlPlane;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 56 final adversarial denial suite (F-043/F-044 exclusions + AI authority boundary):
 * statutory consolidation is denied by design; no autonomous payment, posting, or approval path
 * exists anywhere; AI cannot approve, publish, or hold authority. This suite maps the plan's
 * tests/security/final/ entry point.
 */
class FinalAdversarialTest {

    private final EliminationService eliminations = new EliminationService();
    private final AiControlPlane plane = new AiControlPlane();

    @Test
    void statutoryConsolidationIsDeniedEverywhere() {
        for (String request :
                List.of(
                        "prepare legal consolidation",
                        "produce statutory report",
                        "replace statutory consolidation",
                        "management eliminations presented as statutory")) {
            assertThat(eliminations.statutoryRequest(request))
                    .as("statutory request denied: " + request)
                    .contains("DENIED")
                    .contains("never be represented as statutory");
        }
    }

    @Test
    void noAutonomousFinancialExecutionPathExists() {
        // F-044: there is no API, workflow, timer, or AI path that executes payments,
        // GL posting, certification, publication, or approval. The governed write
        // paths all require a named human: plan versions (PlanVersionService approve),
        // calendar levels (actOnLevel assignee), elimination runs (approvedBy),
        // promotions (independent approver). We assert the denial surfaces:
        AiControlPlane.RouteState killed = plane.disable("operator@epm", "final gate");
        AiControlPlane.Gate gate =
                plane.admit(
                        new AiControlPlane.Invocation(
                                "kpi-qa",
                                new AiControlPlane.CertifiedSnapshot(
                                        "s", "k", "p", "v", true, List.of("E")),
                                "ai@epm"),
                        List.of(
                                new AiControlPlane.Route(
                                        "kpi-qa",
                                        2,
                                        "ref",
                                        "qa-1.0.0",
                                        true,
                                        "council",
                                        List.of("kpi_lookup"),
                                        "eu",
                                        false)),
                        killed,
                        true);
        assertThat(gate.allowed()).isFalse();
        assertThat(gate.reason()).contains("deterministic path active");
        // Numeric authority: AI output with unsupported numbers is invalid.
        assertThat(
                        plane.numericFidelity(
                                "cash is 7777.77",
                                List.of(new AiControlPlane.NumericClaim("cash", "1500.00"))))
                .isFalse();
    }

    @Test
    void eliminationsNeverAutoEliminateMismatches() {
        var matches =
                eliminations.match(
                        List.of(
                                new EliminationService.PlanLine(
                                        "l1",
                                        "E1",
                                        "E2",
                                        "k",
                                        EliminationService.PlanLine.Direction.PAY,
                                        new java.math.BigDecimal("100.00")),
                                new EliminationService.PlanLine(
                                        "l2",
                                        "E2",
                                        "E1",
                                        "k",
                                        EliminationService.PlanLine.Direction.RECEIVE,
                                        new java.math.BigDecimal("70.00"))),
                        new java.math.BigDecimal("0.01"));
        var run =
                eliminations.eliminate(
                        matches, "final-run", "p", "approver@epm", List.of(), id -> id);
        assertThat(run.run().entries()).isEmpty();
        assertThat(run.openedCases()).hasSize(1);
        assertThat(run.note()).contains("never auto-eliminated");
    }
}
