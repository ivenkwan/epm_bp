package dev.epm.reporting.lineage;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Build provenance row in {@code epm_lineage_projection}. */
@Entity
@Table(name = "epm_lineage_projection")
public class LineageProjectionEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "projection_id")
    private Long projectionId;

    @Column(name = "graph_name")
    private String graphName;

    @Column(name = "build_kind")
    private String buildKind;

    @Column(name = "node_count")
    private int nodeCount;

    @Column(name = "edge_count")
    private int edgeCount;

    @Column(name = "source_watermark")
    private OffsetDateTime sourceWatermark;

    @Column(name = "built_at_utc")
    private OffsetDateTime builtAtUtc;

    @Column(name = "built_by")
    private String builtBy;

    public OffsetDateTime getSourceWatermark() {
        return sourceWatermark;
    }

    public String getBuildKind() {
        return buildKind;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    static LineageProjectionEntity record(
            String graphName,
            String buildKind,
            int nodeCount,
            int edgeCount,
            OffsetDateTime sourceWatermark,
            String builtBy) {
        LineageProjectionEntity entity = new LineageProjectionEntity();
        entity.graphName = graphName;
        entity.buildKind = buildKind;
        entity.nodeCount = nodeCount;
        entity.edgeCount = edgeCount;
        entity.sourceWatermark = sourceWatermark;
        entity.builtBy = builtBy;
        entity.builtAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }

    /** Latest build provenance for a graph (the freshness watermark source). */
    public static LineageProjectionEntity latestFor(String graphName) {
        return find("graphName = ?1 order by builtAtUtc desc", graphName).firstResult();
    }
}
