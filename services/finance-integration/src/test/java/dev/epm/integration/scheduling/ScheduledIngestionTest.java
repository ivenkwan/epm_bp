package dev.epm.integration.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.epm.integration.connectors.Connectors;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Task 17 ingestion replay safety: due schedules run; duplicate extracts return the original run
 * and land no duplicate facts; zero-row extracts raise completeness exceptions (never empty
 * certified replacements); operator replay is authorized and idempotent; unregistered connector
 * kinds are rejected; freshness is recorded.
 */
@QuarkusTest
class ScheduledIngestionTest {

    @Inject IngestionService ingestion;

    @Test
    void dueScheduleLandsExtractWithFreshnessAndRunEvidence() {
        String scheduleId = newSchedule(true);
        Connectors.Extract extract =
                new Connectors.Extract(
                        "{\"rows\":3}".getBytes(StandardCharsets.UTF_8),
                        OffsetDateTime.of(2026, 11, 2, 1, 0, 0, 0, ZoneOffset.UTC),
                        3);
        IngestionRunEntity run =
                ingestion.ingest(scheduleId, "API_FEED", extract, "scheduler", null);

        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(run.getRowCount()).isEqualTo(3);
        assertThat(run.getSourceFreshness()).isEqualTo(extract.sourceTimestamp());
        assertThat(run.getLoadId()).isNotNull();
    }

    @Test
    void duplicateExtractReplayReturnsOriginalRunWithoutDuplicateFacts() {
        String scheduleId = newSchedule(true);
        byte[] content = "{\"same\":\"batch\"}".getBytes(StandardCharsets.UTF_8);
        Connectors.Extract extract =
                new Connectors.Extract(content, OffsetDateTime.now(ZoneOffset.UTC), 1);

        IngestionRunEntity first =
                ingestion.ingest(scheduleId, "API_FEED", extract, "scheduler", null);
        IngestionRunEntity second =
                ingestion.ingest(scheduleId, "API_FEED", extract, "scheduler", null);

        assertThat(second.getRunId()).isEqualTo(first.getRunId());
        // Content-addressed landing: one payload, one load identity.
        assertThat(second.getLoadId()).isEqualTo(first.getLoadId());
        assertThat(runCount(scheduleId)).isEqualTo(1);
    }

    @Test
    void zeroRowExtractIsACompletenessExceptionNotAnEmptyLanding() {
        String scheduleId = newSchedule(true);
        Connectors.Extract empty =
                new Connectors.Extract("[]".getBytes(StandardCharsets.UTF_8), null, 0);
        IngestionRunEntity run = ingestion.ingest(scheduleId, "API_FEED", empty, "scheduler", null);

        assertThat(run.getStatus()).isEqualTo("ZERO_ROWS_COMPLETENESS_EXCEPTION");
        assertThat(run.getLoadId()).isNull();
    }

    @Test
    void operatorReplayIsIdempotentAndRequiresANamedOperator() {
        String scheduleId = newSchedule(true);
        byte[] content = "{\"ops\":\"batch\"}".getBytes(StandardCharsets.UTF_8);
        Connectors.Extract extract =
                new Connectors.Extract(content, OffsetDateTime.now(ZoneOffset.UTC), 1);

        IngestionRunEntity original =
                ingestion.ingest(scheduleId, "MANAGED_FILE", extract, "scheduler", null);
        IngestionRunEntity replayed =
                ingestion.replay(scheduleId, "MANAGED_FILE", extract, "operator@example.invalid");

        assertThat(replayed.getRunId()).isEqualTo(original.getRunId());

        Connectors.Extract different =
                new Connectors.Extract(
                        "{\"ops\":\"other\"}".getBytes(StandardCharsets.UTF_8),
                        OffsetDateTime.now(ZoneOffset.UTC),
                        1);
        IngestionRunEntity next =
                ingestion.replay(scheduleId, "MANAGED_FILE", different, "operator@example.invalid");
        assertThat(next.getRunId()).isNotEqualTo(original.getRunId());

        assertThatThrownBy(() -> ingestion.replay(scheduleId, "MANAGED_FILE", different, " "))
                .isInstanceOf(IngestionService.IngestionException.class)
                .hasMessageContaining("named operator");
    }

    @Test
    void unregisteredConnectorKindsAreRejectedByTheAllowlist() {
        byte[] content = "{}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> Connectors.batchIdentity("ARBITRARY_CODE", "SYS-ERP-1", content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowlisted");
    }

    @Transactional
    int runCount(String scheduleId) {
        return (int) IngestionRunEntity.find("scheduleId", scheduleId).count();
    }

    @Transactional
    String newSchedule(boolean enabled) {
        String scheduleId = "sched-" + UUID.randomUUID();
        jakarta.persistence.EntityManager em =
                io.quarkus.hibernate.orm.panache.Panache.getEntityManager();
        em.createNativeQuery(
                        "INSERT INTO epm_ingestion_schedule (schedule_id, contract_id,"
                                + " system_id, cron, enabled) VALUES (?, ?, ?, ?, ?)")
                .setParameter(1, scheduleId)
                .setParameter(2, "treasury-daily-v3")
                .setParameter(3, "SYS-ERP-1")
                .setParameter(4, "0 17 * * *")
                .setParameter(5, enabled)
                .executeUpdate();
        return scheduleId;
    }
}
