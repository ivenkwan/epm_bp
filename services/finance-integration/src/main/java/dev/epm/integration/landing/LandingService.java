package dev.epm.integration.landing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Immutable landing: original payload bytes are stored once (content-addressed), the load log links
 * every submission identity to its payload, contract version in force, run, counts, and both time
 * bases. Duplicate content is flagged for review while the distinct submission identity stays
 * distinguishable; nothing is ever overwritten.
 */
@ApplicationScoped
public class LandingService {

    /** Lands a payload; identical content under a NEW submission id is a duplicate review. */
    @Transactional
    public LandingResult land(
            byte[] content,
            String contentType,
            String systemId,
            String contractId,
            int contractVersion,
            String submissionId,
            String runId,
            int rowCount,
            LocalDateTime sourceTimestamp) {
        String hash = sha256(content);
        LandedPayloadEntity existing = LandedPayloadEntity.byHash(hash);
        boolean duplicate = existing != null;
        LandedPayloadEntity payload =
                duplicate ? existing : LandedPayloadEntity.of(content, contentType, hash);
        String outcome = duplicate ? "DUPLICATE_REVIEW" : "LANDED";
        LoadLogEntity log =
                LoadLogEntity.record(
                        systemId,
                        contractId,
                        contractVersion,
                        submissionId,
                        payload.getPayloadId(),
                        runId,
                        rowCount,
                        sourceTimestamp,
                        outcome);
        return new LandingResult(log.getLoadId(), payload.getPayloadId(), hash, outcome, duplicate);
    }

    /** Retrieves the original payload bytes by load id (auditor journey). */
    @Transactional
    public Optional<LandedPayload> payloadFor(java.util.UUID loadId) {
        LoadLogEntity log = LoadLogEntity.findById(loadId);
        if (log == null) {
            return Optional.empty();
        }
        LandedPayloadEntity payload = LandedPayloadEntity.findById(log.getPayloadId());
        return Optional.of(
                new LandedPayload(
                        payload.getContent(),
                        payload.getContentHash(),
                        log.getSourceTimestamp(),
                        log.getReceivingTimestamp(),
                        log.getOutcome()));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("sha256 unavailable", e);
        }
    }

    /** Landing outcome for one submission identity. */
    public record LandingResult(
            java.util.UUID loadId,
            java.util.UUID payloadId,
            String contentHash,
            String outcome,
            boolean duplicateContent) {}

    /** Original payload read-back with both time bases. */
    public record LandedPayload(
            byte[] content,
            String contentHash,
            LocalDateTime sourceTimestamp,
            LocalDateTime receivingTimestamp,
            String outcome) {}

    @SuppressWarnings("unused")
    private static String unusedCharsetAnchor() {
        return StandardCharsets.UTF_8.name();
    }
}
