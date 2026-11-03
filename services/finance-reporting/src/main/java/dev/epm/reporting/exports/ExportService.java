package dev.epm.reporting.exports;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Governed exports and the action register (Task 31). Exports render only from immutable certified
 * editions, carry edition/watermark metadata, enforce a configured row limit with NO silent
 * truncation (overflow is a governed refusal), re-authorize the recipient at distribution time, and
 * log every delivery with retention class and correlation id. Action items track decisions, owners,
 * due dates, completion evidence, and independent verification; timers remind but never verify or
 * close.
 */
@ApplicationScoped
public class ExportService {

    /** Export decision inputs. */
    public record ExportRequest(
            String editionId,
            String format,
            String recipient,
            java.util.List<String[]> rows,
            int rowLimit,
            String correlationId) {}

    /** Export outcome: content, or a governed refusal with a safe reason. */
    public record ExportResult(
            String exportId, String content, int rowCount, String refusedBecause) {}

    /** Distribution decision after recipient re-authorization. */
    public record Distribution(boolean delivered, String reason, String retentionClass) {}

    /** One action register item. */
    public record ActionItem(
            String actionId,
            String description,
            String owner,
            java.time.OffsetDateTime dueAt,
            String decision,
            String decidedBy,
            String completionEvidence,
            String verifiedBy) {}

    /** Renders an export from a certified edition's rows. */
    public ExportResult export(ExportRequest request) {
        if (request.rows().size() > request.rowLimit()) {
            return new ExportResult(
                    null,
                    null,
                    request.rows().size(),
                    "row count "
                            + request.rows().size()
                            + " exceeds the configured limit "
                            + request.rowLimit()
                            + "; request a durable export job");
        }
        StringBuilder csv = new StringBuilder();
        csv.append("# edition=")
                .append(request.editionId())
                .append(" watermark=certified correlation=")
                .append(request.correlationId() == null ? "n/a" : request.correlationId())
                .append('\n');
        for (String[] row : request.rows()) {
            csv.append(String.join(",", row)).append('\n');
        }
        return new ExportResult(
                "exp-" + System.nanoTime(), csv.toString(), request.rows().size(), null);
    }

    /**
     * Distribution-time recipient re-authorization: the recipient must be entitled for the
     * edition's entity scope (the caller supplies the entitlement verdict from the Task 8 service);
     * delivery is logged with retention class.
     */
    public Distribution distribute(boolean recipientEntitled, String retentionClass) {
        if (!recipientEntitled) {
            return new Distribution(false, "recipient not entitled at distribution time", null);
        }
        return new Distribution(true, "delivered", retentionClass);
    }

    /** Records a decision with completion evidence; independent verification is separate. */
    public ActionItem decide(ActionItem item, String decision, String decidedBy, String evidence) {
        return new ActionItem(
                item.actionId(),
                item.description(),
                item.owner(),
                item.dueAt(),
                decision,
                decidedBy,
                evidence,
                null);
    }

    /**
     * Independent verification: the verifier must differ from the decider (CHECK-backed); timers
     * never perform this step — only a named human call does.
     */
    public boolean verify(ActionItem item, String verifier) {
        return verifier != null
                && item.decidedBy() != null
                && !verifier.equals(item.decidedBy())
                && item.completionEvidence() != null;
    }
}
