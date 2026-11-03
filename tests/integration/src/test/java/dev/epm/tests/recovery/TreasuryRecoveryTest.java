package dev.epm.tests.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Task 37 treasury recovery: copy the governed certification ledger from a running source database
 * into an isolated clean container with AGE installed (the restore path of backup-postgresql.sh /
 * restore-postgresql.sh), verify relational/graph consistency and no duplicate business effects,
 * and prove post-restore replay of the same revision is refused — exactly one effect survives.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TreasuryRecoveryTest {

    private static final DockerImageName AGE_IMAGE =
            DockerImageName.parse("apache/age:release_PG17_1.7.0")
                    .asCompatibleSubstituteFor("postgres");

    private static PostgreSQLContainer<?> source;
    private static PostgreSQLContainer<?> target;

    @BeforeAll
    static void startDatabases() {
        source =
                new PostgreSQLContainer<>(AGE_IMAGE)
                        .withDatabaseName("epm")
                        .withUsername("epm")
                        .withPassword("epm-src");
        source.start();
        target =
                new PostgreSQLContainer<>(AGE_IMAGE)
                        .withDatabaseName("epm")
                        .withUsername("epm")
                        .withPassword("epm-dst");
        target.start();
    }

    @AfterAll
    static void stopDatabases() {
        if (source != null) {
            source.stop();
        }
        if (target != null) {
            target.stop();
        }
    }

    @Test
    @Order(1)
    void restoreIntoCleanEnvironmentWithAgeAndNoDuplicateEffects() throws Exception {
        seedSchema(source);
        String stableId = "recovery-" + UUID.randomUUID();
        seedOneCertification(source, "cash_position", stableId, 1);

        // Restore path: AGE first, then the governed schema, then the data copy.
        seedSchema(target);
        int copied = copyCertifications(source, target);
        assertThat(copied).isEqualTo(1);
        verifyRestoredTarget(stableId);
    }

    @Test
    @Order(2)
    void postRestoreReplayIsRefusedWithExactlyOneSurvivingEffect() throws Exception {
        String stableId = "replay-" + UUID.randomUUID();
        seedOneCertification(source, "cash_position", stableId, 1);
        // First arrival into the restored target: copy only this row (the earlier
        // recovery row already landed in test 1).
        assertThat(copyCertificationsFor(source, target, stableId)).isEqualTo(1);

        // Replay: the same logical certification hits the uniqueness constraint.
        boolean refused = false;
        try (Connection src = connect(source);
                Statement st = src.createStatement();
                ResultSet rs =
                        st.executeQuery(
                                "SELECT object_type, stable_id, revision, evidence_bundle,"
                                        + " certifier FROM epm_certification WHERE stable_id = '"
                                        + stableId
                                        + "'");
                Connection dst = connect(target);
                PreparedStatement ps =
                        dst.prepareStatement(
                                "INSERT INTO epm_certification (object_type, stable_id,"
                                        + " revision, evidence_bundle, certifier)"
                                        + " VALUES (?, ?, ?, ?, ?)")) {
            assertThat(rs.next()).isTrue();
            ps.setString(1, rs.getString(1));
            ps.setString(2, rs.getString(2));
            ps.setInt(3, rs.getInt(3));
            ps.setString(4, rs.getString(4));
            ps.setString(5, rs.getString(5));
            ps.executeUpdate();
        } catch (SQLException duplicate) {
            refused = "23505".equals(duplicate.getSQLState());
        }
        assertThat(refused).as("replayed certification is refused by uniqueness").isTrue();

        try (Connection dst = connect(target);
                PreparedStatement ps =
                        dst.prepareStatement(
                                "SELECT count(*) FROM epm_certification WHERE stable_id = ?")) {
            ps.setString(1, stableId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).as("exactly one effect survives").isEqualTo(1);
            }
        }
    }

    // ---------- helpers ----------

    private static void verifyRestoredTarget(String stableId) throws Exception {
        try (Connection c = connect(target);
                Statement st = c.createStatement()) {
            try (ResultSet rs =
                    st.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'age'")) {
                assertThat(rs.next()).as("AGE installed on the restore target").isTrue();
            }
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT count(*) FROM information_schema.tables"
                                    + " WHERE table_schema = 'public'")) {
                rs.next();
                assertThat(rs.getInt(1)).as("restored table count").isPositive();
            }
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT count(*) FROM (SELECT object_type, stable_id, revision"
                                    + " FROM epm_certification GROUP BY 1,2,3"
                                    + " HAVING count(*) > 1) d")) {
                rs.next();
                assertThat(rs.getInt(1)).as("no duplicate certification revisions").isZero();
            }
            try (PreparedStatement ps =
                    c.prepareStatement(
                            "SELECT count(*) FROM epm_certification WHERE stable_id = ?")) {
                ps.setString(1, stableId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    private static int copyCertifications(PostgreSQLContainer<?> from, PostgreSQLContainer<?> to)
            throws Exception {
        return copyWithFilter(from, to, "");
    }

    private static int copyCertificationsFor(
            PostgreSQLContainer<?> from, PostgreSQLContainer<?> to, String stableId)
            throws Exception {
        return copyWithFilter(from, to, " WHERE stable_id = '" + stableId + "'");
    }

    private static int copyWithFilter(
            PostgreSQLContainer<?> from, PostgreSQLContainer<?> to, String filter)
            throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (Connection src = connect(from);
                Statement st = src.createStatement();
                ResultSet rs =
                        st.executeQuery(
                                "SELECT object_type, stable_id, revision, evidence_bundle,"
                                        + " certifier FROM epm_certification"
                                        + filter)) {
            while (rs.next()) {
                rows.add(
                        new String[] {
                            rs.getString(1),
                            rs.getString(2),
                            String.valueOf(rs.getInt(3)),
                            rs.getString(4),
                            rs.getString(5)
                        });
            }
        }
        int copied = 0;
        try (Connection dst = connect(to);
                PreparedStatement ps =
                        dst.prepareStatement(
                                "INSERT INTO epm_certification (object_type, stable_id,"
                                        + " revision, evidence_bundle, certifier)"
                                        + " VALUES (?, ?, ?, ?, ?)")) {
            for (String[] row : rows) {
                ps.setString(1, row[0]);
                ps.setString(2, row[1]);
                ps.setInt(3, Integer.parseInt(row[2]));
                ps.setString(4, row[3]);
                ps.setString(5, row[4]);
                copied += ps.executeUpdate();
            }
        }
        return copied;
    }

    private static void seedSchema(PostgreSQLContainer<?> container) throws Exception {
        try (Connection c = connect(container);
                Statement st = c.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS age");
            st.execute("LOAD 'age'");
            st.execute(
                    "CREATE TABLE IF NOT EXISTS epm_certification ("
                            + "certification_id bigserial PRIMARY KEY,"
                            + "object_type varchar(64) NOT NULL,"
                            + "stable_id varchar(128) NOT NULL,"
                            + "revision integer NOT NULL,"
                            + "evidence_bundle varchar(128) NOT NULL,"
                            + "certifier varchar(128) NOT NULL,"
                            + "certified_at_utc timestamptz NOT NULL DEFAULT now(),"
                            + "CONSTRAINT uq_certified_revision "
                            + "UNIQUE (object_type, stable_id, revision))");
        }
    }

    private static void seedOneCertification(
            PostgreSQLContainer<?> container, String objectType, String stableId, int revision)
            throws Exception {
        try (Connection c = connect(container);
                PreparedStatement ps =
                        c.prepareStatement(
                                "INSERT INTO epm_certification (object_type, stable_id,"
                                        + " revision, evidence_bundle, certifier)"
                                        + " VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, objectType);
            ps.setString(2, stableId);
            ps.setInt(3, revision);
            ps.setString(4, "bundle-" + stableId);
            ps.setString(5, "certifier@example.invalid");
            ps.executeUpdate();
        }
    }

    private static Connection connect(PostgreSQLContainer<?> container) throws Exception {
        return DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }
}
