package dev.epm.core.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Idempotency for mutations: an {@code Idempotency-Key} is bound to (actor, operation, canonical
 * payload fingerprint). Same key + same fingerprint replays the stored original response; same key
 * + different fingerprint raises a 409 problem. The record commits in the same transaction as the
 * business effect, so rolled-back work never claims a key.
 */
@ApplicationScoped
public class IdempotencyService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSource dataSource;

    @Inject
    IdempotencyService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Looks up the key. Returns a replay when the fingerprint matches, throws a 409 {@link
     * ProblemException} when it does not, and returns empty when the key is new.
     */
    public Optional<StoredResponse> lookup(
            String idempotencyKey, String actor, String operation, String payloadJson) {
        String fingerprint = fingerprint(actor, operation, payloadJson);
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "SELECT fingerprint, response_status, response_body FROM"
                                        + " epm_idempotency WHERE idempotency_key = ?")) {
            ps.setString(1, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String storedFingerprint = rs.getString(1);
                if (!storedFingerprint.equals(fingerprint)) {
                    throw new ProblemException(
                            409,
                            "IDEMPOTENCY_KEY_REUSED",
                            "Idempotency key reuse with different payload",
                            "The Idempotency-Key was already used with a different canonical"
                                    + " payload.",
                            null);
                }
                return Optional.of(new StoredResponse(rs.getInt(2), rs.getString(3)));
            }
        } catch (SQLException e) {
            throw new ApiSafetyException("idempotency lookup failed", e);
        }
    }

    /**
     * Persists the original response inside the caller's transaction. Must be invoked on the same
     * transaction as the business mutation.
     */
    public void store(
            String idempotencyKey,
            String actor,
            String operation,
            String payloadJson,
            int responseStatus,
            String responseBody,
            String correlationId) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "INSERT INTO epm_idempotency (idempotency_key, actor, operation,"
                                        + " fingerprint, response_status, response_body,"
                                        + " correlation_id) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, idempotencyKey);
            ps.setString(2, actor);
            ps.setString(3, operation);
            ps.setString(4, fingerprint(actor, operation, payloadJson));
            ps.setInt(5, responseStatus);
            ps.setString(6, responseBody);
            ps.setString(7, correlationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ApiSafetyException("idempotency store failed", e);
        }
    }

    /**
     * Canonical payload fingerprint bound to actor and operation. The payload is parsed as JSON and
     * re-serialized with recursively sorted object keys so semantically equal payloads with
     * different key order share one fingerprint. The digest is computed over the three byte
     * sequences with separators - never over a concatenated string.
     */
    public String fingerprint(String actor, String operation, String payloadJson) {
        String canonical;
        try {
            JsonNode node =
                    MAPPER.readTree(
                            payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson);
            canonical = sortDeep(node).toString();
        } catch (IOException e) {
            // Not JSON: fingerprint the raw bytes so non-JSON bodies stay idempotent too.
            canonical = payloadJson == null ? "" : payloadJson;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(actor.getBytes(StandardCharsets.UTF_8));
            digest.update(SEPARATOR);
            digest.update(operation.getBytes(StandardCharsets.UTF_8));
            digest.update(SEPARATOR);
            digest.update(canonical.getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new ApiSafetyException("sha256 unavailable", e);
        }
    }

    private static final byte[] SEPARATOR = new byte[] {'|'};

    private static JsonNode sortDeep(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = MAPPER.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            java.util.List<Map.Entry<String, JsonNode>> entries = new java.util.ArrayList<>();
            while (fields.hasNext()) {
                entries.add(fields.next());
            }
            entries.sort(Map.Entry.comparingByKey());
            for (Map.Entry<String, JsonNode> entry : entries) {
                sorted.set(entry.getKey(), sortDeep(entry.getValue()));
            }
            return sorted;
        }
        if (node.isArray()) {
            var array = MAPPER.createArrayNode();
            for (JsonNode item : node) {
                array.add(sortDeep(item));
            }
            return array;
        }
        return node;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new ApiSafetyException("sha256 unavailable", e);
        }
    }

    private static String hex(byte[] hash) {
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(Character.forDigit((b >> 4) & 0xf, 16));
            builder.append(Character.forDigit(b & 0xf, 16));
        }
        return builder.toString();
    }

    /** The original response stored for the key. */
    public record StoredResponse(int status, String body) {}

    /** Wraps infrastructure failures from the idempotency store. */
    public static final class ApiSafetyException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ApiSafetyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
