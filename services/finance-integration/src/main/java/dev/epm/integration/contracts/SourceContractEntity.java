package dev.epm.integration.contracts;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/** Versioned source contract row in {@code epm_source_contract}. */
@Entity
@Table(name = "epm_source_contract")
@IdClass(SourceContractEntity.Key.class)
public class SourceContractEntity extends PanacheEntityBase {

    @Id
    @Column(name = "contract_id")
    private String contractId;

    @Id private Integer version;

    @Column(name = "system_id")
    private String systemId;

    @Column(name = "owner")
    private String owner;

    @Column(name = "scope")
    private String scope;

    @Column(name = "grain")
    private String grain;

    @Column(name = "cadence")
    private String cadence;

    @Column(name = "cutoff_utc")
    private LocalTime cutoffUtc;

    @jakarta.persistence.Column(name = "field_definitions", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String fieldDefinitions;

    @jakarta.persistence.Column(name = "quality_rules", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String qualityRules;

    @jakarta.persistence.Column(name = "reconciliation_basis", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String reconciliationBasis;

    @Column(name = "retention_months")
    private Integer retentionMonths;

    @Column(name = "credentials_ref")
    private String credentialsRef;

    @Column(name = "precedence")
    private Integer precedence;

    private String status;

    @Column(name = "deprecated_at_utc")
    private LocalDateTime deprecatedAtUtc;

    @Column(name = "proposed_by")
    private String proposedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    public String getContractId() {
        return contractId;
    }

    public Integer getVersion() {
        return version;
    }

    public String getScope() {
        return scope;
    }

    public Integer getPrecedence() {
        return precedence;
    }

    public String getStatus() {
        return status;
    }

    public String getGrain() {
        return grain;
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public LocalTime getCutoffUtc() {
        return cutoffUtc;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void setGrain(String grain) {
        this.grain = grain;
    }

    public void setCadence(String cadence) {
        this.cadence = cadence;
    }

    public void setCutoffUtc(LocalTime cutoffUtc) {
        this.cutoffUtc = cutoffUtc;
    }

    public void setFieldDefinitions(String fieldDefinitions) {
        this.fieldDefinitions = fieldDefinitions;
    }

    public void setQualityRules(String qualityRules) {
        this.qualityRules = qualityRules;
    }

    public void setReconciliationBasis(String reconciliationBasis) {
        this.reconciliationBasis = reconciliationBasis;
    }

    public void setRetentionMonths(Integer retentionMonths) {
        this.retentionMonths = retentionMonths;
    }

    public void setCredentialsRef(String credentialsRef) {
        this.credentialsRef = credentialsRef;
    }

    public void setPrecedence(Integer precedence) {
        this.precedence = precedence;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setProposedBy(String proposedBy) {
        this.proposedBy = proposedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    /** Active contracts for a scope, lowest precedence first. */
    public static SourceContractEntity activeForScope(String scope) {
        return find("scope = ?1 and status = ?2 order by precedence", scope, "ACTIVE")
                .firstResult();
    }

    /** Composite key for versioned contracts. */
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;

        private String contractId;
        private Integer version;

        public Key() {
            // JPA
        }

        public Key(String contractId, Integer version) {
            this.contractId = contractId;
            this.version = version;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            Key that = (Key) other;
            return Objects.equals(contractId, that.contractId)
                    && Objects.equals(version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(contractId, version);
        }
    }
}
