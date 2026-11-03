package dev.epm.integration.scheduling;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Durable ingestion run in {@code epm_ingestion_run}. */
@Entity
@Table(name = "epm_ingestion_run")
public class IngestionRunEntity extends PanacheEntityBase {

    @Id
    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "schedule_id")
    private String scheduleId;

    @Column(name = "contract_id")
    private String contractId;

    @Column(name = "system_id")
    private String systemId;

    @Column(name = "batch_identity")
    private String batchIdentity;

    private String status;

    @Column(name = "row_count")
    private int rowCount;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "source_freshness")
    private OffsetDateTime sourceFreshness;

    @Column(name = "load_id")
    private UUID loadId;

    @Column(name = "started_at_utc")
    private OffsetDateTime startedAtUtc;

    @Column(name = "finished_at_utc")
    private OffsetDateTime finishedAtUtc;

    @Column(name = "initiated_by")
    private String initiatedBy;

    @Column(name = "correlation_id")
    private String correlationId;

    public UUID getRunId() {
        return runId;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public String getStatus() {
        return status;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public OffsetDateTime getSourceFreshness() {
        return sourceFreshness;
    }

    public UUID getLoadId() {
        return loadId;
    }

    public String getBatchIdentity() {
        return batchIdentity;
    }

    static IngestionRunEntity start(
            String scheduleId,
            String contractId,
            String systemId,
            String batchIdentity,
            String initiatedBy,
            String correlationId) {
        IngestionRunEntity entity = new IngestionRunEntity();
        entity.runId = UUID.randomUUID();
        entity.scheduleId = scheduleId;
        entity.contractId = contractId;
        entity.systemId = systemId;
        entity.batchIdentity = batchIdentity;
        entity.initiatedBy = initiatedBy;
        entity.correlationId = correlationId;
        entity.status = "RUNNING";
        entity.startedAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }

    void completeSucceeded(int rowCount, OffsetDateTime freshness, UUID loadId) {
        this.status = "SUCCEEDED";
        this.rowCount = rowCount;
        this.sourceFreshness = freshness;
        this.loadId = loadId;
        this.finishedAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    }

    void completeZeroRows() {
        this.status = "ZERO_ROWS_COMPLETENESS_EXCEPTION";
        this.finishedAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    }

    void completeFailed() {
        this.status = "FAILED";
        this.finishedAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    }

    public static IngestionRunEntity findByScheduleAndBatch(
            String scheduleId, String batchIdentity) {
        return find("scheduleId = ?1 and batchIdentity = ?2", scheduleId, batchIdentity)
                .firstResult();
    }
}
