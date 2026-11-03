package dev.epm.core.certification;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Immutable rejection history in {@code epm_certification_rejection}. */
@Entity
@Table(name = "epm_certification_rejection")
public class CertificationRejectionEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rejection_id")
    private Long rejectionId;

    @Column(name = "object_type")
    private String objectType;

    @Column(name = "stable_id")
    private String stableId;

    private int revision;

    private String certifier;

    private String reason;

    @Column(name = "rejected_at_utc")
    private OffsetDateTime rejectedAtUtc;

    static CertificationRejectionEntity record(
            String objectType, String stableId, int revision, String certifier, String reason) {
        CertificationRejectionEntity entity = new CertificationRejectionEntity();
        entity.objectType = objectType;
        entity.stableId = stableId;
        entity.revision = revision;
        entity.certifier = certifier;
        entity.reason = reason;
        entity.rejectedAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }

    public String getReason() {
        return reason;
    }

    public String getCertifier() {
        return certifier;
    }

    public static long countFor(String objectType, String stableId) {
        return find("objectType = ?1 and stableId = ?2", objectType, stableId).count();
    }
}
