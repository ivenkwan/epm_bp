package dev.epm.integration.validation;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Quarantined row with full explanation coordinates in {@code epm_quarantined_row}. */
@Entity
@Table(name = "epm_quarantined_row")
public class QuarantinedRowEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quarantine_id")
    private Long quarantineId;

    @Column(name = "validation_id")
    private UUID validationId;

    @Column(name = "row_number")
    private int rowNumber;

    @Column(name = "rule_name")
    private String ruleName;

    private String expected;

    private String actual;

    private String explanation;

    @Column(name = "case_id")
    private String caseId;

    @Column(name = "source_revision")
    private String sourceRevision;

    public static QuarantinedRowEntity of(
            UUID validationId,
            int rowNumber,
            String ruleName,
            String expected,
            String actual,
            String explanation,
            String sourceRevision) {
        QuarantinedRowEntity entity = new QuarantinedRowEntity();
        entity.validationId = validationId;
        entity.rowNumber = rowNumber;
        entity.ruleName = ruleName;
        entity.expected = expected;
        entity.actual = actual;
        entity.explanation = explanation;
        entity.sourceRevision = sourceRevision;
        entity.persist();
        return entity;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getExpected() {
        return expected;
    }

    public String getActual() {
        return actual;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }
}
