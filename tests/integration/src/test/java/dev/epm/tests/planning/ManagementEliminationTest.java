package dev.epm.tests.planning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.epm.planning.eliminations.EliminationService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 50 acceptance (AC-F-030-01, playbook 7, F-043 exclusion): counterparty lines match within
 * tolerance and eliminate exactly once under an approved run; mismatches and one-sided lines open
 * cases with entity owners — never automatic elimination; the reconciled bridge preserves both
 * sides and all differences; replay never eliminates twice; statutory representation is denied by
 * design.
 */
class ManagementEliminationTest {

    private final EliminationService service = new EliminationService();

    private static EliminationService.PlanLine line(
            String entity, String counterparty, String key, String direction, String amount) {
        return new EliminationService.PlanLine(
                entity + ":" + key,
                entity,
                counterparty,
                key,
                EliminationService.PlanLine.Direction.valueOf(direction),
                new BigDecimal(amount));
    }

    @Test
    void matchedPairsEliminateExactlyOnceUnderApproval() {
        List<EliminationService.MatchResult> matches =
                service.match(
                        List.of(
                                line("ENT-101", "ENT-102", "IC-LOAN-1", "PAY", "500.00"),
                                line("ENT-102", "ENT-101", "IC-LOAN-1", "RECEIVE", "500.00")),
                        new BigDecimal("0.01"));
        assertThat(matches.get(0).status())
                .isEqualTo(EliminationService.MatchResult.Status.MATCHED_WITHIN_TOLERANCE);

        EliminationService.RunOutcome run =
                service.eliminate(
                        matches, "elim-1", "plan-bud-2027:r2", "approver@epm", List.of(), id -> id);
        assertThat(run.executed()).isTrue();
        assertThat(run.run().label()).isEqualTo("MANAGEMENT_ONLY");
        assertThat(run.run().entries()).hasSize(1);
        assertThat(run.run().entries().get(0).eliminated()).isEqualByComparingTo("500.00");
        assertThat(run.run().entries().get(0).trace()).contains("MANAGEMENT_ONLY");

        // Replay of the same run id: refused as a second elimination.
        EliminationService.RunOutcome replay =
                service.eliminate(
                        matches,
                        "elim-1",
                        "plan-bud-2027:r2",
                        "approver@epm",
                        List.of(run.run()),
                        id -> id);
        assertThat(replay.executed()).isFalse();
        assertThat(replay.note()).contains("eliminate twice");

        // No approval: refused.
        assertThat(service.eliminate(matches, "elim-2", "p", " ", List.of(), id -> id).note())
                .contains("independent approval");
    }

    @Test
    void mismatchesAndOneSidedLinesOpenCasesNeverAutoEliminate() {
        List<EliminationService.MatchResult> matches =
                service.match(
                        List.of(
                                line("ENT-101", "ENT-102", "IC-1", "PAY", "500.00"),
                                line("ENT-102", "ENT-101", "IC-1", "RECEIVE", "480.00"),
                                line("ENT-103", "ENT-104", "IC-2", "PAY", "100.00")),
                        new BigDecimal("0.01"));
        assertThat(matches.get(0).status())
                .isEqualTo(EliminationService.MatchResult.Status.MISMATCH_BEYOND_TOLERANCE);
        assertThat(matches.get(1).status())
                .isEqualTo(EliminationService.MatchResult.Status.ONE_SIDED);

        EliminationService.RunOutcome run =
                service.eliminate(matches, "elim-3", "p", "approver@epm", List.of(), id -> id);
        assertThat(run.executed()).isTrue();
        assertThat(run.run().entries()).as("no mismatched or one-sided pair eliminates").isEmpty();
        assertThat(run.openedCases()).hasSize(2);
        assertThat(run.openedCases().get(0).ownerEntity()).isEqualTo("ENT-101");
        assertThat(run.openedCases().get(0).difference()).isEqualTo("20.00");
        assertThat(run.openedCases().get(1).difference()).isEqualTo("one-sided");
    }

    @Test
    void bridgePreservesBothSidesAndAllDifferences() {
        List<EliminationService.MatchResult> matches =
                service.match(
                        List.of(
                                line("ENT-101", "ENT-102", "IC-1", "PAY", "500.00"),
                                line("ENT-102", "ENT-101", "IC-1", "RECEIVE", "500.005")),
                        new BigDecimal("0.01"));
        EliminationService.RunOutcome run =
                service.eliminate(matches, "elim-4", "p", "approver@epm", List.of(), id -> id);
        EliminationService.Bridge bridge = service.bridge(run.run(), matches);
        // Lower side eliminated (500.00); 0.005 difference preserved as residual.
        assertThat(bridge.totalEliminated()).isEqualByComparingTo("500.00");
        assertThat(bridge.totalResidual()).isEqualByComparingTo("0.01");
        assertThat(bridge.perKeyResiduals()).anyMatch(r -> r.startsWith("IC-1:"));
        assertThat(bridge.label()).isEqualTo("MANAGEMENT_ONLY");
    }

    @Test
    void statutoryRepresentationIsDeniedByDesign() {
        String denial = service.statutoryRequest("prepare legal consolidation for group");
        assertThat(denial).contains("DENIED").contains("never be represented as statutory");
    }
}
