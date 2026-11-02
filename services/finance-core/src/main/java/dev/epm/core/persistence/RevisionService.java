package dev.epm.core.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Revision allocation for governed objects. Every business fact is stored as an append-only
 * revision of a stable identity; the database enforces revision uniqueness and this service
 * allocates the next revision under the calling transaction so a business commit, its audit row,
 * and its outbox row commit or roll back together.
 */
@ApplicationScoped
public class RevisionService {

    private final DataSource dataSource;

    @Inject
    RevisionService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Register the object identity if needed; idempotent per (objectType, stableId). */
    @Transactional
    public void ensureObject(String objectType, String stableId) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "INSERT INTO epm_object (object_type, stable_id) VALUES (?, ?) "
                                        + "ON CONFLICT DO NOTHING")) {
            ps.setString(1, objectType);
            ps.setString(2, stableId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("ensureObject failed", e);
        }
    }

    /**
     * Allocate the next revision for the object inside the caller's transaction. Uses a per-object
     * advisory lock so concurrent allocations serialize instead of colliding on the primary key;
     * callers that race still get strictly sequential numbers.
     */
    public int allocateRevision(
            String objectType,
            String stableId,
            String actor,
            String reason,
            String correlationId,
            LocalDate effectiveDate,
            String payloadJson) {
        long lockKey = lockKeyFor(objectType, stableId);
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement lock = c.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                lock.setLong(1, lockKey);
                lock.executeQuery();
            }
            int next = 1;
            try (PreparedStatement max =
                    c.prepareStatement(
                            "SELECT COALESCE(MAX(revision), 0) + 1 FROM epm_revision "
                                    + "WHERE object_type = ? AND stable_id = ?")) {
                max.setString(1, objectType);
                max.setString(2, stableId);
                try (ResultSet rs = max.executeQuery()) {
                    if (rs.next()) {
                        next = rs.getInt(1);
                    }
                }
            }
            try (PreparedStatement insert =
                    c.prepareStatement(
                            "INSERT INTO epm_revision (object_type, stable_id, revision, "
                                    + "effective_date, actor, reason, correlation_id, payload, "
                                    + "payload_hash, recorded_at_utc) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())")) {
                insert.setString(1, objectType);
                insert.setString(2, stableId);
                insert.setInt(3, next);
                insert.setDate(
                        4, effectiveDate == null ? null : java.sql.Date.valueOf(effectiveDate));
                insert.setString(5, actor);
                insert.setString(6, reason);
                insert.setString(7, correlationId);
                insert.setString(8, payloadJson);
                insert.setString(9, sha256Hex(payloadJson));
                insert.executeUpdate();
            }
            return next;
        } catch (SQLException e) {
            throw new PersistenceException("allocateRevision failed", e);
        }
    }

    /** Latest revision number for an object, if any revision exists. */
    public Optional<Integer> latestRevision(String objectType, String stableId) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "SELECT MAX(revision) FROM epm_revision "
                                        + "WHERE object_type = ? AND stable_id = ?")) {
            ps.setString(1, objectType);
            ps.setString(2, stableId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getObject(1) != null) {
                    return Optional.of(rs.getInt(1));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("latestRevision failed", e);
        }
    }

    /** Recorded (receiving) timestamp of a revision, always UTC. */
    public Optional<OffsetDateTime> recordedAtUtc(
            String objectType, String stableId, int revision) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "SELECT recorded_at_utc FROM epm_revision WHERE object_type = ? AND"
                                        + " stable_id = ? AND revision = ?")) {
            ps.setString(1, objectType);
            ps.setString(2, stableId);
            ps.setInt(3, revision);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(rs.getObject(1, OffsetDateTime.class))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("recordedAtUtc failed", e);
        }
    }

    /** Payload hash of a revision, used for tamper evidence and deduplication. */
    public Optional<String> payloadHash(String objectType, String stableId, int revision) {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "SELECT payload_hash FROM epm_revision WHERE object_type = ? AND"
                                        + " stable_id = ? AND revision = ?")) {
            ps.setString(1, objectType);
            ps.setString(2, stableId);
            ps.setInt(3, revision);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("payloadHash failed", e);
        }
    }

    private static long lockKeyFor(String objectType, String stableId) {
        String canonical = objectType + "/" + stableId;
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(StandardCharsets.UTF_8));
            long key = 0;
            for (int i = 0; i < 8; i++) {
                key = (key << 8) | (digest[i] & 0xffL);
            }
            return key;
        } catch (NoSuchAlgorithmException e) {
            throw new PersistenceException("lock key digest unavailable", e);
        }
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new PersistenceException("sha256 unavailable", e);
        }
    }

    /** Wraps SQL failures from the persistence helpers. */
    public static final class PersistenceException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        PersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
