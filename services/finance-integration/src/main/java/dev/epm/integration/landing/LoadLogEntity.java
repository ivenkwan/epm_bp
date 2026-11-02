package dev.epm.integration.landing;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable load-log row in {@code epm_load_log}. */
@Entity
@Table(name = "epm_load_log")
public class LoadLogEntity extends PanacheEntityBase {

    @Id
    @Column(name = "load_id")
    private UUID loadId;

    @Column(name = "source_system_id")
    private String sourceSystemId;

    @Column(name = "contract_id")
    private String contractId;

    @Column(name = "contract_version")
    private Integer contractVersion;

    @Column(name = "submission_id")
    private String submissionId;

    @Column(name = "revision_id")
    private Long revisionId;

    @Column(name = "payload_id")
    private UUID payloadId;

    @Column(name = "run_id")
    private String runId;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "source_timestamp")
    private LocalDateTime sourceTimestamp;

    @Column(name = "receiving_timestamp")
    private LocalDateTime receivingTimestamp;

    private String outcome;

    public static LoadLogEntity record(
            String systemId,
            String contractId,
            Integer contractVersion,
            String submissionId,
            UUID payloadId,
            String runId,
            int rowCount,
            LocalDateTime sourceTimestamp,
            String outcome) {
        LoadLogEntity entity = new LoadLogEntity();
        entity.loadId = UUID.randomUUID();
        entity.sourceSystemId = systemId;
        entity.contractId = contractId;
        entity.contractVersion = contractVersion;
        entity.submissionId = submissionId;
        entity.payloadId = payloadId;
        entity.runId = runId;
        entity.rowCount = rowCount;
        entity.sourceTimestamp = sourceTimestamp;
        entity.receivingTimestamp = LocalDateTime.now(java.time.ZoneOffset.UTC);
        entity.outcome = outcome;
        entity.persist();
        return entity;
    }

    public UUID getLoadId() {
        return loadId;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public String getRunId() {
        return runId;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public LocalDateTime getSourceTimestamp() {
        return sourceTimestamp;
    }

    public LocalDateTime getReceivingTimestamp() {
        return receivingTimestamp;
    }

    public String getOutcome() {
        return outcome;
    }

    public Long getRevisionId() {
        return revisionId;
    }

    public UUID getPayloadId() {
        return payloadId;
    }
}
