package dev.epm.admin.masterdata;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.postgresql.util.PGobject;

/**
 * Append-only master-data change evidence: actor, reason, old/new values with UTC time. Rows land
 * in the shared audit table from Task 5 (immutability enforced there).
 */
@ApplicationScoped
public class MasterDataAudit {

    private final DataSource dataSource;

    @Inject
    MasterDataAudit(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** One evidence row per governed master-data change. */
    @Transactional
    public void append(
            String objectId,
            String action,
            String actor,
            String reason,
            String oldValue,
            String newValue) {
        String sql =
                "INSERT INTO epm_audit_event (object_type, stable_id, revision, actor, "
                        + "action, reason, old_values, new_values, recorded_at_utc) "
                        + "VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "master_data");
            ps.setString(2, objectId);
            ps.setString(3, actor);
            ps.setString(4, action);
            ps.setString(5, reason);
            ps.setObject(6, jsonb(oldValue));
            ps.setObject(7, jsonb(newValue));
            ps.setObject(8, OffsetDateTime.now(ZoneOffset.UTC));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new MasterDataService.MasterDataException(
                    "audit append failed: " + e.getMessage());
        }
    }

    private static PGobject jsonb(String value) throws SQLException {
        PGobject object = new PGobject();
        object.setType("jsonb");
        if (value == null) {
            object.setValue(null);
            return object;
        }
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        object.setValue("\"" + escaped + "\"");
        return object;
    }
}
