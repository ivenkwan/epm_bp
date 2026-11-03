package dev.epm.integration.validation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic validation and quarantine over landed rows (Task 19). The versioned rule set
 * evaluates typed facts side-effect-free; pass/fail outcomes and quarantine rows persist
 * transactionally. Quarantined rows cannot enter a certifiable product: the certification gate
 * (Task 20) requires a passed validation run for the load, and corrected data arrives only as a new
 * revision reprocessed from landing.
 */
@ApplicationScoped
public class ValidationService {

    /** Rule-set version in force for R1 (registry in rules/drools/validation). */
    public static final String RULE_VERSION = "val-1.0.0";

    private volatile ValidationEngine engine;

    private ValidationEngine engine() {
        if (engine == null) {
            engine = ValidationEngine.fromRepository();
        }
        return engine;
    }

    /**
     * Validates one landed batch: evaluates every row, persists the run and every quarantine row
     * with rule name, expected vs actual, and an actionable explanation.
     */
    @Transactional
    public ValidationRunEntity validate(UUID loadId, List<LandedRow> rows, String sourceRevision) {
        ValidationEngine.Result result = engine().evaluate(rows);
        ValidationRunEntity run =
                ValidationRunEntity.record(
                        loadId, RULE_VERSION, result.quarantined.size(), result.passedRows);
        for (ValidationEngine.Quarantine quarantine : result.quarantined) {
            QuarantinedRowEntity.of(
                    run.getValidationId(),
                    quarantine.rowNumber(),
                    quarantine.ruleName(),
                    quarantine.expected(),
                    quarantine.actual(),
                    quarantine.explanation(),
                    sourceRevision);
        }
        return run;
    }

    /** Quarantine rows for one validation run (submitter/steward evidence). */
    @Transactional
    public List<QuarantinedRowEntity> quarantineRows(UUID validationId) {
        return QuarantinedRowEntity.find("validationId", validationId).list();
    }
}
