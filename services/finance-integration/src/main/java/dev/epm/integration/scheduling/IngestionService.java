package dev.epm.integration.scheduling;

import dev.epm.integration.connectors.Connectors;
import dev.epm.integration.landing.LandingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Scheduled ingestion with replay safety (Task 17). Runs are durable rows keyed by (schedule, batch
 * identity): a retried or operator-replayed batch returns the original run and lands no duplicate
 * facts (landing is content-addressed). Zero-row extracts raise a completeness exception rather
 * than an empty certified replacement, and disabled schedules never execute.
 */
@ApplicationScoped
public class IngestionService {

    private final LandingService landing;

    @Inject
    IngestionService(LandingService landing) {
        this.landing = landing;
    }

    /**
     * Executes one ingestion for a schedule and extract. Returns the durable run; a replay of the
     * same batch identity returns the original run untouched.
     */
    @Transactional
    public IngestionRunEntity ingest(
            String scheduleId,
            String connectorKind,
            Connectors.Extract extract,
            String initiatedBy,
            String correlationId) {
        IngestionScheduleEntity schedule = IngestionScheduleEntity.findById(scheduleId);
        if (schedule == null) {
            throw new IngestionException("schedule not found: " + scheduleId);
        }

        String batchIdentity =
                Connectors.batchIdentity(connectorKind, schedule.getSystemId(), extract.content());
        IngestionRunEntity existing =
                IngestionRunEntity.findByScheduleAndBatch(scheduleId, batchIdentity);
        if (existing != null) {
            return existing; // replay: no second execution, no duplicate landed facts
        }

        IngestionRunEntity run =
                IngestionRunEntity.start(
                        scheduleId,
                        schedule.getContractId(),
                        schedule.getSystemId(),
                        batchIdentity,
                        initiatedBy,
                        correlationId);

        if (!schedule.isEnabled()) {
            run.completeFailed();
            return run;
        }

        if (extract.rowCount() == 0) {
            // Zero rows is a completeness exception, never an empty certified product.
            run.completeZeroRows();
            return run;
        }

        try {
            String submissionId = "ingest-" + run.getRunId();
            LandingService.LandingResult landed =
                    landing.land(
                            extract.content(),
                            "application/octet-stream",
                            schedule.getSystemId(),
                            schedule.getContractId(),
                            1,
                            submissionId,
                            "run-" + run.getRunId(),
                            extract.rowCount(),
                            extract.sourceTimestamp() == null
                                    ? null
                                    : extract.sourceTimestamp().toLocalDateTime());
            run.completeSucceeded(extract.rowCount(), extract.sourceTimestamp(), landed.loadId());
            return run;
        } catch (RuntimeException failure) {
            run.completeFailed();
            return run;
        }
    }

    /** Operator replay: same batch identity returns the original run; nothing re-lands. */
    @Transactional
    public IngestionRunEntity replay(
            String scheduleId, String connectorKind, Connectors.Extract extract, String operator) {
        if (operator == null || operator.isBlank()) {
            throw new IngestionException("replay requires a named operator");
        }
        return ingest(scheduleId, connectorKind, extract, operator, null);
    }

    /** Ingestion governance failures. */
    public static final class IngestionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public IngestionException(String message) {
            super(message);
        }
    }
}
