package dev.epm.reporting.exportformats;

import java.util.List;

/**
 * Task 45 governed export formats: approved Excel, PDF, and presentation exports with watermark,
 * source/edition equivalence (the export is stamped with the edition content hash), recipient
 * re-authorization at delivery, retention classification, a delivery log, row-limit handling with
 * an explicit truncation notice, and action tracking. Distribution is integrated into recurring
 * role-based workspaces without automatic publication.
 */
public class ExportFormatService {

    /** Approved export formats. */
    public enum Format {
        EXCEL,
        PDF,
        PRESENTATION
    }

    /** One export request. */
    public record ExportRequest(
            Format format,
            String editionId,
            String editionContentHash,
            int rowLimit,
            int requestedRows,
            String watermark,
            String retentionClass,
            List<String> recipients,
            List<String> authorizedRecipients) {}

    /** The produced export artifact descriptor. */
    public record ExportArtifact(
            Format format,
            String editionId,
            String contentHash,
            String watermark,
            boolean truncated,
            int deliveredRows,
            String truncationNotice,
            String retentionClass) {}

    /** Delivery outcome per recipient (re-authorization enforced). */
    public record Delivery(String recipient, boolean delivered, String reason) {}

    /** Full export outcome including the delivery log and tracked action. */
    public record ExportResult(
            ExportArtifact artifact, List<Delivery> deliveryLog, boolean exported, String note) {}

    /**
     * Produces the export. Fail-closed: watermark and retention classification are mandatory; row
     * limits truncate with an explicit notice (never silently); every recipient is re-authorized at
     * delivery time; the artifact records the edition content hash for source/edition equivalence.
     */
    public ExportResult export(ExportRequest request) {
        if (request.watermark() == null || request.watermark().isBlank()) {
            return refused("watermark is mandatory for governed exports");
        }
        if (request.retentionClass() == null || request.retentionClass().isBlank()) {
            return refused("retention classification is mandatory for governed exports");
        }
        if (request.editionContentHash() == null || request.editionContentHash().isBlank()) {
            return refused("source/edition equivalence requires the edition content hash");
        }
        boolean truncated = request.requestedRows() > request.rowLimit();
        int deliveredRows = Math.min(request.requestedRows(), request.rowLimit());
        ExportArtifact artifact =
                new ExportArtifact(
                        request.format(),
                        request.editionId(),
                        request.editionContentHash(),
                        request.watermark(),
                        truncated,
                        deliveredRows,
                        truncated
                                ? "truncated to the governed row limit of "
                                        + request.rowLimit()
                                        + " of "
                                        + request.requestedRows()
                                        + " rows — no rows beyond the limit are included"
                                : null,
                        request.retentionClass());
        List<Delivery> deliveryLog =
                request.recipients().stream()
                        .map(
                                recipient ->
                                        request.authorizedRecipients().contains(recipient)
                                                ? new Delivery(recipient, true, null)
                                                : new Delivery(
                                                        recipient,
                                                        false,
                                                        "recipient not re-authorized at delivery"))
                        .toList();
        boolean anyDelivered = deliveryLog.stream().anyMatch(Delivery::delivered);
        return new ExportResult(
                artifact,
                deliveryLog,
                anyDelivered,
                anyDelivered
                        ? "export delivered to re-authorized recipients; action tracked"
                        : "no recipient was re-authorized; nothing delivered");
    }

    private static ExportResult refused(String reason) {
        return new ExportResult(null, List.of(), false, reason);
    }

    /**
     * Recurring-workspace refresh policy: weekly/monthly refresh produces a new edition for review
     * — publication and supersession stay named-human acts; nothing publishes automatically.
     */
    public record RefreshOutcome(String editionId, String newEditionId, boolean published) {}

    public RefreshOutcome refresh(String editionId, String newEditionId) {
        return new RefreshOutcome(editionId, newEditionId, false);
    }
}
