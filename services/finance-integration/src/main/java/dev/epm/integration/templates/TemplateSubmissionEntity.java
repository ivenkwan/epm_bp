package dev.epm.integration.templates;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Versioned template submission in {@code epm_template_submission}. */
@Entity
@Table(name = "epm_template_submission")
public class TemplateSubmissionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "submission_id")
    private String submissionId;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "contract_id")
    private String contractId;

    @Column(name = "source_period")
    private String sourcePeriod;

    @Column(name = "template_version")
    private String templateVersion;

    private int revision;

    private String status;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "submitted_at_utc")
    private OffsetDateTime submittedAtUtc;

    @Column(name = "load_id")
    private UUID loadId;

    public String getSubmissionId() {
        return submissionId;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getSourcePeriod() {
        return sourcePeriod;
    }

    public int getRevision() {
        return revision;
    }

    public String getStatus() {
        return status;
    }

    public UUID getLoadId() {
        return loadId;
    }

    static TemplateSubmissionEntity open(
            String submissionId,
            String entityId,
            String contractId,
            String sourcePeriod,
            String templateVersion,
            int revision,
            String submittedBy) {
        TemplateSubmissionEntity entity = new TemplateSubmissionEntity();
        entity.submissionId = submissionId;
        entity.entityId = entityId;
        entity.contractId = contractId;
        entity.sourcePeriod = sourcePeriod;
        entity.templateVersion = templateVersion;
        entity.revision = revision;
        entity.status = "VALIDATING";
        entity.submittedBy = submittedBy;
        entity.submittedAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }

    void accept(UUID loadId) {
        this.status = "ACCEPTED";
        this.loadId = loadId;
    }

    void reject() {
        this.status = "REJECTED";
    }

    public static int latestRevision(String entityId, String contractId, String sourcePeriod) {
        TemplateSubmissionEntity latest =
                find(
                                "entityId = ?1 and contractId = ?2 and sourcePeriod = ?3"
                                        + " order by revision desc",
                                entityId,
                                contractId,
                                sourcePeriod)
                        .firstResult();
        return latest == null ? 0 : latest.revision;
    }

    public static TemplateSubmissionEntity findBySubmissionId(String submissionId) {
        return findById(submissionId);
    }
}
