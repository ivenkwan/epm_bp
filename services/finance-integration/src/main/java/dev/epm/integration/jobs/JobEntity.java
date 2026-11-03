package dev.epm.integration.jobs;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** Durable job row in {@code epm_job} (Task 6 schema V003). */
@Entity
@Table(name = "epm_job")
public class JobEntity extends PanacheEntityBase {

    @Id
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "job_type")
    private String jobType;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody = "{}";

    @Column(name = "body_digest")
    private String bodyDigest;

    private String status;

    @Column(name = "progress_percent")
    private int progressPercent;

    @Column(name = "execution_count")
    private int executionCount;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "submitted_at_utc")
    private OffsetDateTime submittedAtUtc;

    public UUID getJobId() {
        return jobId;
    }

    public String getJobType() {
        return jobType;
    }

    public String getStatus() {
        return status;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public int getExecutionCount() {
        return executionCount;
    }

    public Optional<UUID> jobIdOptional() {
        return Optional.ofNullable(jobId);
    }

    public static JobEntity findByTypeAndDigest(String jobType, String bodyDigest) {
        return find("jobType = ?1 and bodyDigest = ?2", jobType, bodyDigest).firstResult();
    }

    public static JobEntity findByJobId(UUID jobId) {
        return find("jobId", jobId).firstResult();
    }

    static JobEntity queued(String jobType, String bodyDigest, String submittedBy) {
        JobEntity entity = new JobEntity();
        entity.jobId = UUID.randomUUID();
        entity.jobType = jobType;
        entity.bodyDigest = bodyDigest;
        entity.status = "QUEUED";
        entity.progressPercent = 0;
        entity.executionCount = 0;
        entity.submittedBy = submittedBy;
        entity.submittedAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }

    /** Exactly-once claim: only a QUEUED job transitions to RUNNING (counter increments). */
    boolean claimForExecution() {
        if (!"QUEUED".equals(status)) {
            return false;
        }
        status = "RUNNING";
        progressPercent = 10;
        executionCount++;
        return true;
    }

    void finishSucceeded() {
        status = "SUCCEEDED";
        progressPercent = 100;
    }

    void markFailed(String error) {
        status = "FAILED";
    }
}
