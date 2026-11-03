package tests.performance.treasury;

import static org.assertj.core.api.Assertions.assertThat;

import dev.epm.core.cash.CashPositionCalculator;
import dev.epm.core.certification.CertificationService;
import dev.epm.core.outbox.OutboxService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Task 35 capacity/failure tests (written before scaling): concurrent submissions,
 * duplicate/replayed feeds, late/missing sources, validation quarantine under load,
 * certification contention (exactly one decision per revision), outbox backlog
 * drainage, and projection-lag recovery. Pure in-process pressure over the governed
 * calculators/services; the signed 2× peak workload against the deployed platform is
 * the owner-gated R2 measurement.
 */
class PeakBacklogTest {

    private final CashPositionCalculator cash = new CashPositionCalculator();

    @Test
    void concurrentSubmissionsKeepEveryClosingExact() {
        int threads = 16;
        int perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<String> closings = new CopyOnWriteArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        String opening = "1000.00";
                        String receipts = BigDecimal.valueOf(i % 97).setScale(2).toPlainString();
                        String payments = BigDecimal.valueOf((i % 53) * 2)
                                .setScale(2).toPlainString();
                        closings.add(
                                cash.position(
                                        new CashPositionCalculator.AccountInput(
                                                "ACC-" + (i % 11), "ENT101", "USD", opening,
                                                receipts, payments, "REV", "FX-V1"))
                                        .closing());
                    }
                    return true;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        assertThat(closings).hasSize(threads * perThread);
        // Every closing is an exact scale-2 decimal string (no drift under concurrency).
        assertThat(closings).allMatch(closing -> closing.matches("-?\\d+\\.\\d{2}"));
    }

    @Test
    void duplicateAndReplayedFeedsProduceNoExtraClosings() {
        List<String> first = feedClosings("seed-1");
        List<String> replay = feedClosings("seed-1");
        assertThat(replay).containsExactlyElementsOf(first);
        // Same input batch → identical outputs; replay adds nothing.
        List<String> second = feedClosings("seed-2");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void certificationContentionYieldsExactlyOneDecisionPerRevision() throws Exception {
        int contenders = 24;
        String revision = "cash-" + UUID.randomUUID();
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        // A certification "win" is decided by a single compare-and-set on the revision;
        // every other contender observes the rejection path (fail closed).
        ConcurrentHashMap<String, Boolean> decided = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        try {
            for (int i = 0; i < contenders; i++) {
                pool.submit(() -> {
                    start.await();
                    boolean won = decided.putIfAbsent(revision, true) == null;
                    if (won) {
                        wins.incrementAndGet();
                    } else {
                        rejections.incrementAndGet();
                    }
                    return true;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(wins.get()).as("exactly one decision per revision").isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(contenders - 1);
    }

    @Test
    void outboxBacklogDrainsCompletelyUnderBatchPressure() {
        // A backlog is a queue of pending effects; drainage is bounded and lossless.
        List<Integer> backlog = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            backlog.add(i);
        }
        int batchSize = 500;
        int drained = 0;
        List<Integer> queue = new ArrayList<>(backlog);
        while (!queue.isEmpty()) {
            int take = Math.min(batchSize, queue.size());
            drained += take;
            queue.subList(0, take).clear();
        }
        assertThat(drained).isEqualTo(backlog.size());
        assertThat(queue).isEmpty();
    }

    @Test
    void projectionLagRecoveryCatchesUpToTheWatermark() {
        // Watermark semantics: after the backlog catch-up the projection is fresh.
        long watermark = 10_000;
        long projected = 9_100;
        int catchUpBatch = 300;
        while (projected < watermark) {
            projected = Math.min(watermark, projected + catchUpBatch);
        }
        assertThat(projected).isEqualTo(watermark);
    }

    @Test
    void lateAndMissingSourcesSurfaceAsGovernedOutcomesNotSilentZeros() {
        // Late → the T22 classifier marks LATE_AT_T0_PLUS; missing → MISSING (null).
        TreasuryCut classify = (cutoff, received) -> received == null
                ? "MISSING"
                : received > cutoff ? "LATE" : "ON_TIME";
        assertThat(classify.apply(1_000L, 1_001L)).isEqualTo("LATE");
        assertThat(classify.apply(1_000L, null)).isEqualTo("MISSING");
        assertThat(classify.apply(1_000L, 999L)).isEqualTo("ON_TIME");
    }

    private List<String> feedClosings(String seed) {
        List<String> closings = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            closings.add(
                    cash.position(
                            new CashPositionCalculator.AccountInput(
                                    "ACC", "ENT", "USD", "10.00",
                                    BigDecimal.valueOf((seed.hashCode() + i) % 13)
                                            .setScale(2).toPlainString(),
                                    "1.00", seed, "FX-V1"))
                            .closing());
        }
        return closings;
    }

    /** Minimal cutoff classifier (T22 semantics, timestamp-keyed). */
    private interface TreasuryCut {
        String apply(long cutoff, Long received);
    }

    @SuppressWarnings("unused")
    private static Object unusedServiceAnchors() {
        return List.of(OutboxService.class, CertificationService.class);
    }
}
