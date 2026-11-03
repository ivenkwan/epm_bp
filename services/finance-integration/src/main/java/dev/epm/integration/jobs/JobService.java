package dev.epm.integration.jobs;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable long-running jobs (Task 6 contract, runtime landing with R1 ingestion): work that would
 * hold a web request open returns {@code 202} with a job resource. The (type, body digest)
 * uniqueness plus the caller's idempotency record means a replayed submission returns the same job
 * and never executes twice; the QUEUED→RUNNING claim makes execution exactly-once even across
 * worker restarts.
 */
@ApplicationScoped
public class JobService {

    /** Registered work handlers keyed by job type; each is idempotent by design. */
    public interface JobHandler {
        String jobType();

        /** Executes the registered work; must be safe to retry after a crash. */
        void run(UUID jobId);
    }

    private final java.util.Map<String, JobHandler> handlers =
            new java.util.concurrent.ConcurrentHashMap<>();

    public void register(JobHandler handler) {
        handlers.put(handler.jobType(), handler);
    }

    /** Creates (or returns the existing) durable job for this type and canonical digest. */
    @Transactional
    public JobEntity submit(String jobType, String bodyDigest, String submittedBy) {
        JobEntity existing = JobEntity.findByTypeAndDigest(jobType, bodyDigest);
        if (existing != null) {
            return existing; // replay: same job, no second execution
        }
        try {
            return JobEntity.queued(jobType, bodyDigest, submittedBy);
        } catch (jakarta.persistence.PersistenceException raced) {
            JobEntity winner = JobEntity.findByTypeAndDigest(jobType, bodyDigest);
            if (winner != null) {
                return winner;
            }
            throw raced;
        }
    }

    /** Reads one job resource for polling. */
    @Transactional
    public Optional<JobEntity> get(UUID jobId) {
        return Optional.ofNullable(JobEntity.findByJobId(jobId));
    }

    /**
     * Scheduler-driven worker: claims QUEUED jobs and runs their registered handler. The claim
     * guard makes this safe to run on any number of workers.
     */
    /** One worker pass: claims and runs all QUEUED jobs (also driven by the scheduler). */
    @Transactional
    public void processOnce() {
        for (JobEntity job : JobEntity.<JobEntity>find("status", "QUEUED").list()) {
            if (job.claimForExecution()) {
                JobHandler handler = handlers.get(job.getJobType());
                if (handler == null) {
                    job.markFailed("no handler registered");
                    continue;
                }
                try {
                    handler.run(job.getJobId());
                    job.finishSucceeded();
                } catch (RuntimeException failure) {
                    job.markFailed(failure.getMessage());
                }
            }
        }
    }

    @Scheduled(every = "1s")
    void scheduled() {
        processOnce();
    }
}
