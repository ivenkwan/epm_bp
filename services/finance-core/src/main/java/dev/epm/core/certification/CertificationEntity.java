package dev.epm.core.certification;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Independent human certification in {@code epm_certification}. */
@Entity
@Table(name = "epm_certification")
public class CertificationEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "certification_id")
    private Long certificationId;

    @Column(name = "object_type")
    private String objectType;

    @Column(name = "stable_id")
    private String stableId;

    private int revision;

    @Column(name = "validation_run_id")
    private java.util.UUID validationRunId;

    @Column(name = "reconciliation_id")
    private String reconciliationId;

    @Column(name = "evidence_bundle")
    private String evidenceBundle;

    private String certifier;

    @Column(name = "certified_at_utc")
    private OffsetDateTime certifiedAtUtc;

    @Column(name = "correlation_id")
    private String correlationId;

    public String getCertifier() {
        return certifier;
    }

    public int getRevision() {
        return revision;
    }

    public String getStableId() {
        return stableId;
    }

    public String getObjectType() {
        return objectType;
    }

    static CertificationEntity record(
            String objectType,
            String stableId,
            int revision,
            java.util.UUID validationRunId,
            String reconciliationId,
            String evidenceBundle,
            String certifier,
            String correlationId) {
        CertificationEntity entity = new CertificationEntity();
        entity.objectType = objectType;
        entity.stableId = stableId;
        entity.revision = revision;
        entity.validationRunId = validationRunId;
        entity.reconciliationId = reconciliationId;
        entity.evidenceBundle = evidenceBundle;
        entity.certifier = certifier;
        entity.correlationId = correlationId;
        entity.certifiedAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }

    public static CertificationEntity findCertified(String objectType, String stableId) {
        return find(
                        "objectType = ?1 and stableId = ?2 order by revision desc",
                        objectType,
                        stableId)
                .firstResult();
    }
}
