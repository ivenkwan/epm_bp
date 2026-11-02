package dev.epm.core.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Appends immutable audit evidence inside the caller's transaction. Old/new values and approvals
 * are stored verbatim; the database trigger and revoked privileges make the evidence impossible to
 * rewrite through the application role.
 */
@ApplicationScoped
public class AuditService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSource dataSource;

    @Inject
    AuditService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** One audit row with full before/after values and approval linkage. */
    public long append(
            String objectType,
            String stableId,
            int revision,
            String actor,
            AuditAction action,
            String reason,
            String correlationId,
            Map<String, String> oldValues,
            Map<String, String> newValues,
            List<String> approvals) {
        String sql =
                "INSERT INTO epm_audit_event (object_type, stable_id, revision, actor, action,"
                    + " reason, correlation_id, old_values, new_values, approvals, recorded_at_utc)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, now()) RETURNING"
                    + " audit_id";
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, objectType);
            ps.setString(2, stableId);
            ps.setInt(3, revision);
            ps.setString(4, actor);
            ps.setString(5, action.name());
            ps.setString(6, reason);
            ps.setString(7, correlationId);
            ps.setString(8, toJson(oldValues));
            ps.setString(9, toJson(newValues));
            ps.setString(10, toJsonList(approvals));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new AuditException("append failed", e);
        }
    }

    /** Reads one audit row back as evidence: actor, reason, both value sets, approvals. */
    public Optional<AuditRecord> byId(long auditId) {
        String sql =
                "SELECT audit_id, object_type, stable_id, revision, actor, action, reason, "
                        + "correlation_id, old_values, new_values, approvals, recorded_at_utc "
                        + "FROM epm_audit_event WHERE audit_id = ?";
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, auditId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(
                        new AuditRecord(
                                rs.getLong("audit_id"),
                                rs.getString("object_type"),
                                rs.getString("stable_id"),
                                rs.getInt("revision"),
                                rs.getString("actor"),
                                AuditAction.valueOf(rs.getString("action")),
                                rs.getString("reason"),
                                rs.getString("correlation_id"),
                                fromJson(rs.getString("old_values")),
                                fromJson(rs.getString("new_values")),
                                fromJsonList(rs.getString("approvals")),
                                rs.getObject("recorded_at_utc", java.time.OffsetDateTime.class)));
            }
        } catch (SQLException e) {
            throw new AuditException("byId failed", e);
        }
    }

    /** Counts tampering attempts recorded by the guard trigger. */
    public int violationCount() {
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement("SELECT count(*) FROM epm_audit_violation");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new AuditException("violationCount failed", e);
        }
    }

    private static String toJson(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (IOException e) {
            throw new AuditException("unreadable audit values", e);
        }
    }

    private static String toJsonList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (IOException e) {
            throw new AuditException("unreadable audit approvals", e);
        }
    }

    private static Map<String, String> fromJson(String raw) {
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(raw, new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (IOException e) {
            throw new AuditException("unreadable audit values", e);
        }
    }

    private static List<String> fromJsonList(String raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(raw, new TypeReference<ArrayList<String>>() {});
        } catch (IOException e) {
            throw new AuditException("unreadable audit approvals", e);
        }
    }

    /** Audit actions recognized by the database check constraint. */
    public enum AuditAction {
        CREATE,
        UPDATE,
        APPROVE,
        CERTIFY,
        REJECT,
        PUBLISH,
        REVERSE,
        ADJUST
    }

    /** Immutable read-back of one audit evidence row. */
    public record AuditRecord(
            long auditId,
            String objectType,
            String stableId,
            int revision,
            String actor,
            AuditAction action,
            String reason,
            String correlationId,
            Map<String, String> oldValues,
            Map<String, String> newValues,
            List<String> approvals,
            java.time.OffsetDateTime recordedAtUtc) {}

    /** Wraps SQL failures from the audit helpers. */
    public static final class AuditException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        AuditException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
