package dev.epm.integration.validation;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Durable validation run in {@code epm_validation_run}. */
@Entity
@Table(name = "epm_validation_run")
public class ValidationRunEntity extends PanacheEntityBase {

    @Id
    @Column(name = "validation_id")
    private UUID validationId;

    @Column(name = "load_id")
    private UUID loadId;

    @Column(name = "rule_version")
    private String ruleVersion;

    private boolean passed;

    @Column(name = "quarantined_rows")
    private int quarantinedRows;

    @Column(name = "passed_rows")
    private int passedRows;

    @Column(name = "evaluated_at_utc")
    private OffsetDateTime evaluatedAtUtc;

    public UUID getValidationId() {
        return validationId;
    }

    public boolean isPassed() {
        return passed;
    }

    public int getQuarantinedRows() {
        return quarantinedRows;
    }

    public int getPassedRows() {
        return passedRows;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    static ValidationRunEntity record(
            UUID loadId, String ruleVersion, int quarantined, int passed) {
        ValidationRunEntity entity = new ValidationRunEntity();
        entity.validationId = UUID.randomUUID();
        entity.loadId = loadId;
        entity.ruleVersion = ruleVersion;
        entity.passed = quarantined == 0;
        entity.quarantinedRows = quarantined;
        entity.passedRows = passed;
        entity.evaluatedAtUtc = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        entity.persist();
        return entity;
    }

    public static List<ValidationRunEntity> forLoad(UUID loadId) {
        return find("loadId", loadId).list();
    }
}
