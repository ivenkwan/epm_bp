package dev.epm.admin.mappingstudio;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Task 57 mapping studio: business users maintain effective-dated mapping tables (including chart
 * of accounts), accounts, and group mappings as governed configuration — never vendor-side code
 * changes. Every change appends a versioned row with old/new evidence (no history loss);
 * overlapping intervals, duplicate keys, group-integrity violations, and unauthorized editors are
 * refused with actionable errors leaving active mappings untouched; rollback is a new version.
 * Historical requests resolve only to the version effective at their date.
 */
public class MappingStudioService {

    /** One mapping table (from {@code epm_mapping_table}). */
    public record MappingTable(String tableId, String kind, String owner) {}

    /** A proposed mapping version (timeline entry). */
    public record MappingVersion(
            String tableId,
            int version,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String proposedBy,
            String approvedBy,
            String reason) {

        public MappingVersion activatedBy(String approver) {
            return new MappingVersion(
                    tableId, version, effectiveFrom, effectiveTo, proposedBy, approver, reason);
        }
    }

    /** One mapping row inside a version. */
    public record MappingRow(String rowKey, String mapsTo, String groupRef) {}

    /** A proposal: version + rows + scope, submitted through the visual studio. */
    public record Proposal(MappingVersion version, List<MappingRow> rows) {}

    /** Proposal validation outcome. */
    public record ProposalOutcome(boolean accepted, String reason, Proposal proposal) {}

    /**
     * Validates a proposal: entitlement, non-overlap with active versions (adjacent boundary
     * sharing allowed), duplicate row keys, and group integrity — exactly one group per row in
     * GROUP_MAPPING tables.
     */
    public ProposalOutcome propose(
            Proposal proposal,
            MappingTable table,
            List<String> entitledEditors,
            List<MappingVersion> existingVersions) {
        if (!entitledEditors.contains(proposal.version().proposedBy())) {
            return new ProposalOutcome(
                    false, "actor not entitled to edit this mapping table", proposal);
        }
        if (proposal.rows().isEmpty()) {
            return new ProposalOutcome(false, "empty proposal — nothing to map", proposal);
        }
        List<String> keys = proposal.rows().stream().map(MappingRow::rowKey).toList();
        if (keys.stream().distinct().count() != keys.size()) {
            return new ProposalOutcome(false, "duplicate row keys in proposal", proposal);
        }
        if ("GROUP_MAPPING".equals(table.kind())) {
            boolean ungrouped =
                    proposal.rows().stream()
                            .anyMatch(r -> r.groupRef() == null || r.groupRef().isBlank());
            if (ungrouped) {
                return new ProposalOutcome(
                        false,
                        "group mapping requires exactly one governed group per row",
                        proposal);
            }
        }
        for (MappingVersion existing : existingVersions) {
            boolean overlaps =
                    proposal.version()
                                    .effectiveFrom()
                                    .isBefore(
                                            existing.effectiveTo() == null
                                                    ? LocalDate.MAX
                                                    : existing.effectiveTo())
                            && (proposal.version().effectiveTo() == null
                                    || proposal.version()
                                            .effectiveTo()
                                            .isAfter(existing.effectiveFrom()));
            if (overlaps) {
                return new ProposalOutcome(
                        false,
                        "effective dates overlap active version v"
                                + existing.version()
                                + " ["
                                + existing.effectiveFrom()
                                + ", "
                                + (existing.effectiveTo() == null ? "∞" : existing.effectiveTo())
                                + ")",
                        proposal);
            }
        }
        return new ProposalOutcome(true, "proposal accepted for approval", proposal);
    }

    /** Activation: independent approver (never the proposer) activates a proposal. */
    public record Activation(MappingVersion activated, boolean activatedFlag, String note) {}

    public Activation activate(Proposal proposal, String approver) {
        if (approver.equals(proposal.version().proposedBy())) {
            return new Activation(
                    null, false, "self-approval denied: approver must differ from proposer");
        }
        return new Activation(
                proposal.version().activatedBy(approver), true, "activated with provenance");
    }

    /** Rollback: a new version that supersedes forward — history is never deleted. */
    public Proposal rollback(
            MappingVersion active,
            List<MappingRow> previousRows,
            LocalDate effectiveFrom,
            String actor) {
        MappingVersion rollbackVersion =
                new MappingVersion(
                        active.tableId(),
                        active.version() + 1,
                        effectiveFrom,
                        null,
                        actor,
                        null,
                        "rollback to the mapping effective before v" + active.version());
        return new Proposal(rollbackVersion, new ArrayList<>(previousRows));
    }

    /** Historical resolution: only the version effective at the requested date applies. */
    public MappingVersion resolveAt(List<MappingVersion> versions, LocalDate date) {
        return versions.stream()
                .filter(
                        v ->
                                !date.isBefore(v.effectiveFrom())
                                        && (v.effectiveTo() == null
                                                || date.isBefore(v.effectiveTo())))
                .reduce((a, b) -> b)
                .orElse(null);
    }

    /** Mass-change impact preview: counts and keys affected before anything lands. */
    public record ImpactPreview(int changedRows, List<String> affectedKeys, String note) {}

    public ImpactPreview previewImpact(List<MappingRow> current, List<MappingRow> proposed) {
        List<String> affected = new ArrayList<>();
        for (MappingRow row : proposed) {
            current.stream()
                    .filter(c -> c.rowKey().equals(row.rowKey()))
                    .filter(c -> !c.equals(row))
                    .findFirst()
                    .ifPresent(c -> affected.add(row.rowKey()));
        }
        return new ImpactPreview(
                affected.size(),
                affected,
                "preview only — nothing changes until approval and activation");
    }
}
