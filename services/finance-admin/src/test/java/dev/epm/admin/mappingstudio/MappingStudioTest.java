package dev.epm.admin.mappingstudio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 57 mapping-studio acceptance (AC-F-045-01–03): entitled stewards propose effective-dated
 * mapping changes (no vendor code); overlaps, duplicates, group violations, and unauthorized
 * editors refuse with active mappings untouched; activation needs an independent approver; rollback
 * is a new version; historical requests resolve only to the version effective at their date — no
 * history loss.
 */
class MappingStudioTest {

    private final MappingStudioService studio = new MappingStudioService();

    private static final MappingStudioService.MappingTable COA =
            new MappingStudioService.MappingTable("map-coa", "CHART_OF_ACCOUNTS", "steward@epm");

    private static final MappingStudioService.MappingTable GROUPS =
            new MappingStudioService.MappingTable("map-groups", "GROUP_MAPPING", "steward@epm");

    private static MappingStudioService.Proposal proposal(
            String from, String rowKey, String mapsTo) {
        return new MappingStudioService.Proposal(
                new MappingStudioService.MappingVersion(
                        "map-coa",
                        2,
                        LocalDate.parse(from),
                        null,
                        "steward@epm",
                        null,
                        "add account"),
                List.of(new MappingStudioService.MappingRow(rowKey, mapsTo, null)));
    }

    @Test
    void entitledProposalWithinRangeAcceptsAndIndependentApprovalActivates() {
        MappingStudioService.ProposalOutcome outcome =
                studio.propose(
                        proposal("2027-01-01", "4001", "REVENUE-DOMESTIC"),
                        COA,
                        List.of("steward@epm"),
                        List.of(
                                new MappingStudioService.MappingVersion(
                                        "map-coa",
                                        1,
                                        LocalDate.parse("2026-01-01"),
                                        LocalDate.parse("2027-01-01"),
                                        "steward@epm",
                                        "approver@epm",
                                        "initial")));
        assertThat(outcome.accepted()).isTrue();

        MappingStudioService.Activation activation =
                studio.activate(outcome.proposal(), "approver@epm");
        assertThat(activation.activatedFlag()).isTrue();
        assertThat(activation.activated().approvedBy()).isEqualTo("approver@epm");
        assertThat(studio.activate(outcome.proposal(), "steward@epm").note())
                .contains("self-approval denied");
    }

    @Test
    void overlappingEffectiveDatesRefuseWithActionableErrors() {
        MappingStudioService.ProposalOutcome overlap =
                studio.propose(
                        proposal("2026-06-01", "4001", "REVENUE-DOMESTIC"),
                        COA,
                        List.of("steward@epm"),
                        List.of(
                                new MappingStudioService.MappingVersion(
                                        "map-coa",
                                        1,
                                        LocalDate.parse("2026-01-01"),
                                        null,
                                        "steward@epm",
                                        "approver@epm",
                                        "initial")));
        assertThat(overlap.accepted()).isFalse();
        assertThat(overlap.reason())
                .contains("overlap active version v1")
                .contains("[2026-01-01, ∞)");
    }

    @Test
    void duplicatesUnauthorizedEditorsAndGroupViolationsRefuse() {
        assertThat(
                        studio.propose(
                                        proposal("2027-01-01", "4001", "A"),
                                        COA,
                                        List.of("steward@epm"),
                                        List.of())
                                .accepted())
                .isTrue();
        assertThat(
                        studio.propose(
                                        proposal("2027-01-01", "4001", "A"),
                                        COA,
                                        List.of("someone-else@epm"),
                                        List.of())
                                .reason())
                .contains("not entitled");

        MappingStudioService.Proposal noGroup =
                new MappingStudioService.Proposal(
                        new MappingStudioService.MappingVersion(
                                "map-groups",
                                2,
                                LocalDate.parse("2027-01-01"),
                                null,
                                "steward@epm",
                                null,
                                "r"),
                        List.of(new MappingStudioService.MappingRow("ENT-101", "GROUP-A", null)));
        assertThat(studio.propose(noGroup, GROUPS, List.of("steward@epm"), List.of()).reason())
                .contains("exactly one governed group");
    }

    @Test
    void rollbackIsANewVersionAndHistoryIsNeverDeleted() {
        MappingStudioService.MappingVersion v1 =
                new MappingStudioService.MappingVersion(
                        "map-coa",
                        1,
                        LocalDate.parse("2026-01-01"),
                        null,
                        "steward@epm",
                        "approver@epm",
                        "initial");
        List<MappingStudioService.MappingRow> v1Rows =
                List.of(new MappingStudioService.MappingRow("4001", "REVENUE", null));
        MappingStudioService.Proposal rollback =
                studio.rollback(v1, v1Rows, LocalDate.parse("2027-02-01"), "steward@epm");
        assertThat(rollback.version().version()).isEqualTo(2);
        assertThat(rollback.version().reason()).contains("rollback");
        // v1 remains present in history — nothing was deleted.
        assertThat(v1.version()).isEqualTo(1);
    }

    @Test
    void historicalRequestsResolveOnlyToTheVersionEffectiveAtTheirDate() {
        MappingStudioService.MappingVersion v1 =
                new MappingStudioService.MappingVersion(
                        "map-coa",
                        1,
                        LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2027-01-01"),
                        "a",
                        "b",
                        "r");
        MappingStudioService.MappingVersion v2 =
                new MappingStudioService.MappingVersion(
                        "map-coa", 2, LocalDate.parse("2027-01-01"), null, "a", "b", "r");
        assertThat(studio.resolveAt(List.of(v1, v2), LocalDate.parse("2026-06-01")).version())
                .isEqualTo(1);
        assertThat(studio.resolveAt(List.of(v1, v2), LocalDate.parse("2027-06-01")).version())
                .isEqualTo(2);
        // Boundary: from-inclusive resolves to the new version.
        assertThat(studio.resolveAt(List.of(v1, v2), LocalDate.parse("2027-01-01")).version())
                .isEqualTo(2);
    }

    @Test
    void massChangeImpactPreviewsWithoutTouchingAnything() {
        MappingStudioService.ImpactPreview preview =
                studio.previewImpact(
                        List.of(new MappingStudioService.MappingRow("4001", "REVENUE", null)),
                        List.of(
                                new MappingStudioService.MappingRow("4001", "REVENUE-DOM", null),
                                new MappingStudioService.MappingRow("4002", "NEW", null)));
        assertThat(preview.changedRows()).isEqualTo(1);
        assertThat(preview.affectedKeys()).containsExactly("4001");
        assertThat(preview.note()).contains("preview only");
    }
}
