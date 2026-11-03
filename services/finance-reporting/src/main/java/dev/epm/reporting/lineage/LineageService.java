package dev.epm.reporting.lineage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Rebuildable AGE lineage projection (Task 21). The graph is a projection of relational evidence:
 * it can be destroyed and rebuilt at any time without changing financial facts. Builds are
 * provenance-recorded with a relational watermark; dependent certification is gated on watermark
 * freshness (a stale projection blocks).
 */
@ApplicationScoped
public class LineageService {

    /** Governed graph name (matches the platform init and rebuild script). */
    public static final String GRAPH = "epm_lineage";

    /** One projected lineage node or edge (stable ids with relational provenance). */
    public record LineageItem(String kind, String stableId, String relatedStableId) {}

    private final DataSource dataSource;

    @Inject
    LineageService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Full rebuild: drops and recreates the graph from the governed relational sets, then records
     * provenance. Idempotent by construction.
     */
    @Transactional
    public LineageProjectionEntity rebuild(String builtBy) {
        try (Connection c = dataSource.getConnection()) {
            ensureGraph(c);
            clearGraph(c);
            int nodes = 0;
            int edges = 0;
            for (LineageItem item : relationalEvidence(c)) {
                if (item.relatedStableId() == null) {
                    createNode(c, item);
                    nodes++;
                } else {
                    createEdge(c, item);
                    edges++;
                }
            }
            return LineageProjectionEntity.record(
                    GRAPH,
                    "FULL_REBUILD",
                    nodes,
                    edges,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    builtBy);
        } catch (SQLException e) {
            throw new LineageException("rebuild failed", e);
        }
    }

    /**
     * Upstream/downstream impact paths from a stable id. Results reflect entitlements only through
     * the caller's evidence (row scope is applied at query time upstream); staleness is surfaced
     * through {@link #isFresh}.
     */
    public List<String> downstreamOf(String stableId) {
        String sql =
                "SELECT * FROM ag_catalog.cypher('"
                        + GRAPH
                        + "', $$ "
                        + "MATCH (a {stableId: '"
                        + safeId(stableId)
                        + "'})-[*1..5]->(d) "
                        + "RETURN d.stableId $$) AS (id ag_catalog.agtype)";
        List<String> downstream = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                downstream.add(rs.getString(1).replace("\"", ""));
            }
            return downstream;
        } catch (SQLException e) {
            throw new LineageException("downstream query failed", e);
        }
    }

    /** True when the latest projection watermark is at or after the required instant. */
    @Transactional
    public boolean isFresh(OffsetDateTime requiredThrough) {
        LineageProjectionEntity latest = LineageProjectionEntity.latestFor(GRAPH);
        return latest != null && !latest.getSourceWatermark().isBefore(requiredThrough);
    }

    private static void ensureGraph(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS age");
            st.execute("LOAD 'age'");
            st.execute("SET search_path = ag_catalog, \"$user\", public");
            st.execute(
                    "SELECT ag_catalog.create_graph('"
                            + GRAPH
                            + "') WHERE NOT EXISTS (SELECT 1 FROM ag_catalog.ag_graph"
                            + " WHERE name = '"
                            + GRAPH
                            + "')");
        }
    }

    private static void clearGraph(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("SELECT ag_catalog.drop_graph('" + GRAPH + "', true)");
            st.execute("SELECT ag_catalog.create_graph('" + GRAPH + "')");
        }
    }

    /** Relational evidence set: entities as nodes, hierarchy parents as edges. */
    private static List<LineageItem> relationalEvidence(Connection c) throws SQLException {
        List<LineageItem> items = new ArrayList<>();
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT entity_id FROM epm_entity_version")) {
            while (rs.next()) {
                items.add(new LineageItem("Entity", rs.getString(1), null));
            }
        }
        try (Statement st = c.createStatement();
                ResultSet rs =
                        st.executeQuery(
                                "SELECT entity_id, parent_entity_id FROM epm_hierarchy_node"
                                        + " WHERE parent_entity_id IS NOT NULL")) {
            while (rs.next()) {
                items.add(new LineageItem("ROLLS_UP_TO", rs.getString(1), rs.getString(2)));
            }
        }
        return items;
    }

    private static void createNode(Connection c, LineageItem item) throws SQLException {
        String sql =
                "SELECT * FROM ag_catalog.cypher('"
                        + GRAPH
                        + "', $$ "
                        + "CREATE (:Entity {stableId: '"
                        + safeId(item.stableId())
                        + "', kind: '"
                        + item.kind()
                        + "'}) $$) AS (v ag_catalog.agtype)";
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static void createEdge(Connection c, LineageItem item) throws SQLException {
        String sql =
                "SELECT * FROM ag_catalog.cypher('"
                        + GRAPH
                        + "', $$ "
                        + "MATCH (a:Entity {stableId: '"
                        + safeId(item.stableId())
                        + "'}), (b:Entity {stableId: '"
                        + safeId(item.relatedStableId())
                        + "'}) "
                        + "CREATE (a)-[:ROLLS_UP_TO]->(b) $$) AS (v ag_catalog.agtype)";
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /** Stable ids are internal identifiers; quote-stripping keeps the literal inert. */
    private static String safeId(String id) {
        return id == null ? "" : id.replace("'", "");
    }

    /** Lineage governance failures. */
    public static final class LineageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public LineageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
