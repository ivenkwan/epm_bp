package dev.epm.integration.connectors;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Set;

/**
 * Allowlisted source connectors. Only registered connector kinds run; arbitrary connector code or
 * URLs outside approved policy cannot reach the ingestion path (the allowlist is closed and
 * compile-time).
 */
public final class Connectors {

    /** Approved connector kinds (R1: API feed, managed file, template upload). */
    public static final Set<String> ALLOWED = Set.of("API_FEED", "MANAGED_FILE", "TEMPLATE_UPLOAD");

    private Connectors() {}

    /** One extract fetched from a registered source; content is opaque bytes. */
    public record Extract(byte[] content, OffsetDateTime sourceTimestamp, int rowCount) {}

    /** Stable batch identity for replay detection: kind + system + content hash. */
    public static String batchIdentity(String connectorKind, String systemId, byte[] content) {
        if (!ALLOWED.contains(connectorKind)) {
            throw new IllegalArgumentException("connector kind not allowlisted: " + connectorKind);
        }
        return connectorKind + ":" + systemId + ":" + sha256(content);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("sha256 unavailable", e);
        }
    }

    @SuppressWarnings("unused")
    private static String unused() {
        return StandardCharsets.UTF_8.name();
    }
}
