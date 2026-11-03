package dev.epm.admin.cases;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable exception case row in {@code epm_case} (PostgreSQL owns case state). */
@Entity
@Table(name = "epm_case")
public class CaseEntity extends PanacheEntityBase {

    @Id
    @Column(name = "case_id")
    private String caseId;

    @Column(name = "case_type")
    private String caseType;

    private String severity;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "epm_case_state")
    private CaseState status;

    @Column(name = "business_object_type")
    private String businessObjectType;

    @Column(name = "business_object_id")
    private String businessObjectId;

    private String reporter;

    @Column(name = "accountable_owner")
    private String accountableOwner;

    @Column(name = "assigned_resolver")
    private String assignedResolver;

    @Column(name = "required_approver")
    private String requiredApprover;

    @Column(name = "independent_verifier")
    private String independentVerifier;

    @JdbcTypeCode(SqlTypes.JSON)
    private String trigger;

    @JdbcTypeCode(SqlTypes.JSON)
    private String evidence;

    @JdbcTypeCode(SqlTypes.JSON)
    private String decision;

    @JdbcTypeCode(SqlTypes.JSON)
    private String outcome;

    @Column(name = "process_instance_id")
    private String processInstanceId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "created_at_utc")
    private LocalDateTime createdAtUtc;

    @Column(name = "updated_at_utc")
    private LocalDateTime updatedAtUtc;

    public static CaseEntity open(
            String caseId,
            String caseType,
            String severity,
            String businessObjectType,
            String businessObjectId,
            String reporter,
            String triggerJson,
            String correlationId) {
        CaseEntity entity = new CaseEntity();
        entity.caseId = caseId;
        entity.caseType = caseType;
        entity.severity = severity;
        entity.status = CaseState.OPEN;
        entity.businessObjectType = businessObjectType;
        entity.businessObjectId = businessObjectId;
        entity.reporter = reporter;
        entity.trigger = triggerJson;
        entity.evidence = "[]";
        entity.createdAtUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);
        entity.updatedAtUtc = entity.createdAtUtc;
        entity.correlationId = correlationId;
        entity.persist();
        CaseTransitionEntity.record(caseId, "NONE", "OPEN", reporter, "case opened");
        return entity;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getStatus() {
        return status == null ? null : status.name();
    }

    public void setStatus(String status) {
        this.status = CaseState.valueOf(status);
    }

    public String getSeverity() {
        return severity;
    }

    public String getReporter() {
        return reporter;
    }

    public String getAssignedResolver() {
        return assignedResolver;
    }

    public void setAssignedResolver(String assignedResolver) {
        this.assignedResolver = assignedResolver;
    }

    public String getRequiredApprover() {
        return requiredApprover;
    }

    public void setRequiredApprover(String requiredApprover) {
        this.requiredApprover = requiredApprover;
    }

    public String getIndependentVerifier() {
        return independentVerifier;
    }

    public void setIndependentVerifier(String independentVerifier) {
        this.independentVerifier = independentVerifier;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void touch() {
        this.updatedAtUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    /** Database enum for the PRD case state machine. */
    public enum CaseState {
        OPEN,
        TRIAGED,
        ASSIGNED,
        IN_PROGRESS,
        WAITING_SOURCE,
        WAITING_EVIDENCE,
        WAITING_APPROVAL,
        RESOLUTION_PROPOSED,
        RESOLUTION_APPROVED,
        REPROCESSING,
        VERIFIED,
        CLOSED,
        CANCELLED
    }
}
