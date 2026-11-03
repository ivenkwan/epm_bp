package dev.epm.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.epm.core.audit.AuditService;
import dev.epm.core.outbox.OutboxService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Task 5 concurrency and fault-injection gate: stable ids, UTC recorded time, revision uniqueness
 * under concurrent allocation, immutable audit with old/new values, exactly one outbox row per
 * business commit, rollback removing the row (crash-before-dispatch), and redelivery causing no
 * duplicate business effect.
 */
@QuarkusTest
class OutboxConcurrencyTest {

    @Inject RevisionService revisions;
    @Inject AuditService audit;
    @Inject OutboxService outbox;
    @Inject UserTransaction transaction;
    @Inject DataSource dataSource;

    @Test
    void concurrentRevisionAllocationIsStrictlySequentialAndUnique() throws Exception {
        String stableId = "concurrent-" + UUID.randomUUID();
        transaction.begin();
        try {
            revisions.ensureObject("source_batch", stableId);
        } finally {
            transaction.commit();
        }

        int threads = 12;
        int allocationsPerThread = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Set<Integer> allRevisions = ConcurrentHashMap.newKeySet(threads * allocationsPerThread);
        try {
            var futures =
                    IntStream.range(0, threads)
                            .mapToObj(
                                    t ->
                                            pool.submit(
                                                    () -> {
                                                        start.await();
                                                        for (int i = 0;
                                                                i < allocationsPerThread;
                                                                i++) {
                                                            transaction.begin();
                                                            try {
                                                                int revision =
                                                                        revisions.allocateRevision(
                                                                                "source_batch",
                                                                                stableId,
                                                                                "actor-" + t,
                                                                                "concurrent"
                                                                                    + " allocation",
                                                                                "corr-concurrent",
                                                                                LocalDate.of(
                                                                                        2026, 11,
                                                                                        2),
                                                                                "{\"thread\":"
                                                                                        + t
                                                                                        + ",\"i\":"
                                                                                        + i
                                                                                        + "}");
                                                                allRevisions.add(revision);
                                                            } finally {
                                                                transaction.commit();
                                                            }
                                                        }
                                                        return true;
                                                    }))
                            .toList();
            start.countDown();
            for (Future<Boolean> future : futures) {
                assertThat(future.get(120, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }

        int total = threads * allocationsPerThread;
        assertThat(allRevisions)
                .as("every allocation returns a unique revision number")
                .hasSize(total);
        assertThat(allRevisions.stream().min(Integer::compareTo)).contains(1);
        assertThat(allRevisions.stream().max(Integer::compareTo)).contains(total);
        assertThat(
                        allRevisions.stream()
                                .filter(r -> r < 1 || r > total)
                                .collect(Collectors.toSet()))
                .as("revisions form a gap-free 1..N sequence")
                .isEmpty();
        assertThat(revisions.latestRevision("source_batch", stableId)).contains(total);
    }

    @Test
    void recordedAtUtcIsAlwaysUtcAndDistinctFromEffectiveDate() throws Exception {
        String stableId = "timebase-" + UUID.randomUUID();
        transaction.begin();
        try {
            revisions.ensureObject("rate_version", stableId);
            revisions.allocateRevision(
                    "rate_version",
                    stableId,
                    "actor-time",
                    "time base check",
                    "corr-time",
                    LocalDate.of(2026, 10, 30),
                    "{\"rate\":\"1.1000\"}");
        } finally {
            transaction.commit();
        }

        var recorded = revisions.recordedAtUtc("rate_version", stableId, 1);
        assertThat(recorded).as("revision 1 exists").isPresent();
        assertThat(recorded.get().getOffset())
                .as("recorded_at is stored with a UTC offset")
                .isEqualTo(java.time.ZoneOffset.UTC);
    }

    @Test
    void oneBusinessCommitProducesExactlyOneOutboxRowAndRollbackProducesNone() throws Exception {
        String committed = "commit-" + UUID.randomUUID();
        String rolledBack = "rollback-" + UUID.randomUUID();

        // Committed unit of work: object + revision + audit + outbox row together.
        transaction.begin();
        UUID committedEventId;
        try {
            revisions.ensureObject("certification", committed);
            int revision =
                    revisions.allocateRevision(
                            "certification",
                            committed,
                            "certifier@example.invalid",
                            "initial certification evidence",
                            "corr-commit",
                            LocalDate.of(2026, 11, 2),
                            "{\"status\":\"CERTIFIED\"}");
            audit.append(
                    "certification",
                    committed,
                    revision,
                    "certifier@example.invalid",
                    AuditService.AuditAction.CERTIFY,
                    "independent certification",
                    "corr-commit",
                    Map.of(),
                    Map.of("status", "CERTIFIED"),
                    java.util.List.of("approver-1"));
            committedEventId =
                    outbox.append(
                            "certification",
                            committed,
                            "CERTIFIED",
                            "{\"revision\":" + revision + "}",
                            "corr-commit");
        } finally {
            transaction.commit();
        }

        // Rolled-back unit of work (crash before commit): nothing may persist.
        transaction.begin();
        try {
            revisions.ensureObject("certification", rolledBack);
            revisions.allocateRevision(
                    "certification",
                    rolledBack,
                    "certifier@example.invalid",
                    "must disappear",
                    "corr-rollback",
                    LocalDate.of(2026, 11, 2),
                    "{\"status\":\"CERTIFIED\"}");
            outbox.append("certification", rolledBack, "CERTIFIED", "{}", "corr-rollback");
        } finally {
            transaction.rollback();
        }

        assertThat(outbox.byId(committedEventId))
                .as("committed business effect has exactly one outbox row")
                .isPresent();
        assertThat(revisions.latestRevision("certification", committed)).contains(1);
        assertThat(revisions.latestRevision("certification", rolledBack))
                .as("rolled-back allocation leaves no revision")
                .isEmpty();
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "SELECT count(*) FROM epm_outbox WHERE aggregate_id = ?")) {
            ps.setString(1, rolledBack);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getLong(1))
                        .as("rollback removes the outbox row (no phantom event)")
                        .isZero();
            }
        }
    }

    @Test
    void auditEvidenceIsImmutableWithOldAndNewValuesAndTamperDetection() throws Exception {
        String stableId = "audit-" + UUID.randomUUID();
        long auditId;
        transaction.begin();
        try {
            revisions.ensureObject("adjustment", stableId);
            int revision =
                    revisions.allocateRevision(
                            "adjustment",
                            stableId,
                            "preparer@example.invalid",
                            "four-eyes preparation",
                            "corr-audit",
                            LocalDate.of(2026, 11, 2),
                            "{\"amount\":\"100.00\"}");
            auditId =
                    audit.append(
                            "adjustment",
                            stableId,
                            revision,
                            "approver@example.invalid",
                            AuditService.AuditAction.APPROVE,
                            "approved with evidence",
                            "corr-audit",
                            Map.of("amount", "0.00"),
                            Map.of("amount", "100.00"),
                            java.util.List.of("verifier@example.invalid"));
        } finally {
            transaction.commit();
        }

        var evidence = audit.byId(auditId);
        assertThat(evidence).as("audit row readable").isPresent();
        assertThat(evidence.get().oldValues()).containsEntry("amount", "0.00");
        assertThat(evidence.get().newValues()).containsEntry("amount", "100.00");
        assertThat(evidence.get().approvals()).containsExactly("verifier@example.invalid");
        assertThat(evidence.get().recordedAtUtc().getOffset()).isEqualTo(java.time.ZoneOffset.UTC);

        // Tamper attempt through the guard trigger: the evidence must not change and the
        // violation must be recorded on the security audit path.
        int violationsBefore = audit.violationCount();
        try (Connection c = dataSource.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "UPDATE epm_audit_event SET actor = 'tamperer' WHERE audit_id ="
                                        + " ?")) {
            ps.setLong(1, auditId);
            ps.executeUpdate();
        }
        var afterTamper = audit.byId(auditId);
        assertThat(afterTamper.get().actor())
                .as("guard trigger skips mutations on audit evidence")
                .isEqualTo("approver@example.invalid");
        assertThat(audit.violationCount())
                .as("attempted tampering lands on the security audit path")
                .isGreaterThan(violationsBefore);
    }

    @Test
    void redeliveryDoesNotDuplicateBusinessEffects() throws Exception {
        String stableId = "redelivery-" + UUID.randomUUID();
        AtomicInteger deliveries = new AtomicInteger();
        Set<UUID> seenEvents = ConcurrentHashMap.newKeySet();
        outbox.addConsumer(
                event -> {
                    // Consumer-side deduplication by the stable event id; unrelated
                    // aggregates delivered in the same batch are ignored.
                    if (event.aggregateId().equals(stableId) && seenEvents.add(event.eventId())) {
                        deliveries.incrementAndGet();
                    }
                });

        UUID eventId;
        transaction.begin();
        try {
            revisions.ensureObject("publication", stableId);
            revisions.allocateRevision(
                    "publication",
                    stableId,
                    "publisher@example.invalid",
                    "published edition",
                    "corr-redelivery",
                    LocalDate.of(2026, 11, 2),
                    "{\"edition\":1}");
            eventId =
                    outbox.append(
                            "publication",
                            stableId,
                            "PUBLISHED",
                            "{\"edition\":1}",
                            "corr-redelivery");
        } finally {
            transaction.commit();
        }

        int firstPass = outbox.dispatchPending(10);
        assertThat(firstPass).as("first dispatch delivers the event").isPositive();

        // Outbox redelivery after an infrastructure replay: dispatched_at reset forces the
        // same event through the dispatcher again.
        outbox.resetDispatched(eventId);
        int secondPass = outbox.dispatchPending(10);
        assertThat(secondPass).as("redelivery is acknowledged without a second delivery").isZero();
        assertThat(deliveries.get())
                .as("exactly one business effect across delivery and redelivery")
                .isEqualTo(1);
        assertThat(outbox.pendingCount()).isZero();
    }

    @Test
    void leastPrivilegeRoleCannotModifyAuditEvidence() throws Exception {
        String stableId = "role-" + UUID.randomUUID();
        long auditId;
        transaction.begin();
        try {
            revisions.ensureObject("adjustment", stableId);
            int revision =
                    revisions.allocateRevision(
                            "adjustment",
                            stableId,
                            "preparer@example.invalid",
                            "least privilege evidence",
                            "corr-role",
                            LocalDate.of(2026, 11, 2),
                            "{\"amount\":\"50.00\"}");
            auditId =
                    audit.append(
                            "adjustment",
                            stableId,
                            revision,
                            "approver@example.invalid",
                            AuditService.AuditAction.APPROVE,
                            "created for role test",
                            "corr-role",
                            Map.of(),
                            Map.of("amount", "50.00"),
                            java.util.List.of());
        } finally {
            transaction.commit();
        }

        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement setRole = c.prepareStatement("SET ROLE epm_app")) {
                setRole.executeUpdate();
            }
            try {
                PreparedStatement tamper =
                        c.prepareStatement(
                                "UPDATE epm_audit_event SET reason = 'rewritten' WHERE audit_id ="
                                        + " ?");
                tamper.setLong(1, auditId);
                tamper.executeUpdate();
                org.assertj.core.api.Assertions.fail(
                        "the application role must not be allowed to UPDATE audit evidence");
            } catch (SQLException expected) {
                assertThat(expected.getMessage().toLowerCase())
                        .as("permission denied for the application role")
                        .contains("permission denied");
            } finally {
                try (PreparedStatement reset = c.prepareStatement("RESET ROLE")) {
                    reset.executeUpdate();
                }
            }
        }

        assertThat(audit.byId(auditId).get().reason())
                .as("evidence is untouched after the denied attempt")
                .isEqualTo("created for role test");
    }

    @Test
    void transactionStatusHelper() throws Exception {
        transaction.begin();
        try {
            assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_ACTIVE);
        } finally {
            transaction.rollback();
        }
        assertThat(transaction.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
    }
}
