package dev.epm.integration.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * Task 6 deferred contract (O-B6) now landed: same type+digest returns one durable job without
 * duplicate execution; replay after completion returns the same job.
 */
@QuarkusTest
class JobServiceTest {

    @Inject JobService jobs;

    @Test
    void replayedSubmissionReturnsSameJobWithoutDuplicateExecution() {
        List<UUID> executed = new CopyOnWriteArrayList<>();
        jobs.register(
                new JobService.JobHandler() {
                    @Override
                    public String jobType() {
                        return "NOOP_TEST";
                    }

                    @Override
                    public void run(UUID jobId) {
                        executed.add(jobId);
                    }
                });

        String digest = "digest-" + System.nanoTime();
        JobEntity first = jobs.submit("NOOP_TEST", digest, "preparer@example.invalid");
        JobEntity replay = jobs.submit("NOOP_TEST", digest, "preparer@example.invalid");

        assertThat(replay.getJobId()).isEqualTo(first.getJobId());
        // Worker claim discipline: exactly-once execution per job row.
        jobs.processOnce();
        jobs.processOnce();
        JobEntity after = jobs.get(first.getJobId()).orElseThrow();
        assertThat(after.getExecutionCount()).isEqualTo(1);
        assertThat(executed).hasSize(1);
        assertThat(after.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(after.getProgressPercent()).isEqualTo(100);
    }
}
