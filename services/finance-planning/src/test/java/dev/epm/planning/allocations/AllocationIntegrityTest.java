package dev.epm.planning.allocations;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Task 49 acceptance (AC-F-029-01): each step preserves the allocated total exactly; every receiver
 * traces to its source pool; residuals round by largest remainder; zero pools are legitimate;
 * invalid sequences are refused; replay is idempotent; reversal is a linked compensating run that
 * never overwrites the original ledger.
 */
class AllocationIntegrityTest {

    private final AllocationService service = new AllocationService();

    private static AllocationService.Step step(
            int number, String sender, Object... receiverWeightPairs) {
        var receivers = new java.util.ArrayList<AllocationService.Receiver>();
        for (int i = 0; i < receiverWeightPairs.length; i += 2) {
            receivers.add(
                    new AllocationService.Receiver(
                            (String) receiverWeightPairs[i],
                            new BigDecimal((String) receiverWeightPairs[i + 1])));
        }
        return new AllocationService.Step(number, sender, receivers);
    }

    @Test
    void eachStepPreservesTheAllocatedTotalWithLargestRemainderResiduals() {
        // 100.00 across three equal drivers → 33.34 / 33.33 / 33.33 (stable first row wins).
        AllocationService.Model model =
                new AllocationService.Model(
                        "alloc-1",
                        List.of(
                                step(
                                        1, "POOL-IT", "ENT-101", "33.33", "ENT-102", "33.33",
                                        "ENT-103", "33.33")));
        AllocationService.Execution execution =
                service.execute(
                        model,
                        new AllocationService.Pools(Map.of("POOL-IT", new BigDecimal("100.00"))),
                        "run-1",
                        "operator@epm",
                        List.of());
        assertThat(execution.executed()).isTrue();
        List<AllocationService.Entry> entries = execution.run().entries();
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).amount()).isEqualByComparingTo("33.34");
        assertThat(entries.get(1).amount()).isEqualByComparingTo("33.33");
        assertThat(entries.get(2).amount()).isEqualByComparingTo("33.33");
        assertThat(service.allocatedByStep(execution.run()).get(1))
                .as("step total preserved exactly")
                .isEqualByComparingTo("100.00");
        assertThat(entries.get(0).trace()).contains("POOL-IT → ENT-101").contains("33.33/99.99");
    }

    @Test
    void multiStepRunWithStepDownExcludesEarlierSenders() {
        // Step 1: IT → sales/finance; step 2: sales (now a sender) cannot receive again;
        // finance (not a sender yet) may receive in step 2? No — step 2's pool is SALES
        // allocating onward; earlier-sender exclusion applies to receiving.
        AllocationService.Model model =
                new AllocationService.Model(
                        "alloc-2",
                        List.of(
                                step(1, "POOL-IT", "ENT-SALES", "60", "ENT-FIN", "40"),
                                step(2, "ENT-SALES", "ENT-101", "70", "ENT-SALES", "30")));
        AllocationService.Execution execution =
                service.execute(
                        model,
                        new AllocationService.Pools(Map.of("POOL-IT", new BigDecimal("200.00"))),
                        "run-2",
                        "operator@epm",
                        List.of());
        assertThat(execution.executed()).isTrue();
        // Step 1: 200.00 → sales 120.00, fin 80.00. Step 2: sales is a sender so its own
        // self-receivership is excluded; 120.00 flows entirely to ENT-101.
        Map<Integer, BigDecimal> byStep = service.allocatedByStep(execution.run());
        assertThat(byStep.get(1)).isEqualByComparingTo("200.00");
        assertThat(byStep.get(2)).isEqualByComparingTo("120.00");
        assertThat(
                        execution.run().entries().stream()
                                .filter(e -> e.step() == 2 && e.receiver().equals("ENT-SALES"))
                                .count())
                .as("step-down excludes earlier senders from receiving")
                .isZero();
    }

    @Test
    void zeroAndEmptyPoolsAreLegitimateNoOps() {
        AllocationService.Model model =
                new AllocationService.Model(
                        "alloc-3", List.of(step(1, "POOL-EMPTY", "ENT-101", "100")));
        AllocationService.Execution execution =
                service.execute(
                        model,
                        new AllocationService.Pools(Map.of("POOL-EMPTY", BigDecimal.ZERO)),
                        "run-3",
                        "operator@epm",
                        List.of());
        assertThat(execution.executed()).isTrue();
        assertThat(execution.run().entries()).isEmpty();
    }

    @Test
    void invalidSequencesAndUnknownPoolsAreRefused() {
        AllocationService.Model wrongOrder =
                new AllocationService.Model("alloc-4", List.of(step(2, "POOL-IT", "ENT-101", "1")));
        assertThat(
                        service.execute(
                                        wrongOrder,
                                        new AllocationService.Pools(
                                                Map.of("POOL-IT", BigDecimal.ONE)),
                                        "run-4",
                                        "op@epm",
                                        List.of())
                                .note())
                .contains("invalid sequence");

        AllocationService.Model unknownPool =
                new AllocationService.Model("alloc-5", List.of(step(1, "POOL-X", "ENT-101", "1")));
        assertThat(
                        service.execute(
                                        unknownPool,
                                        new AllocationService.Pools(Map.of()),
                                        "run-5",
                                        "op@epm",
                                        List.of())
                                .note())
                .contains("unknown source pool");
    }

    @Test
    void replayingARunIdLoadsNoNewEntries() {
        AllocationService.Model model =
                new AllocationService.Model(
                        "alloc-6", List.of(step(1, "POOL-IT", "ENT-101", "100")));
        AllocationService.Pools pools =
                new AllocationService.Pools(Map.of("POOL-IT", new BigDecimal("50.00")));
        AllocationService.Run first =
                service.execute(model, pools, "run-6", "op@epm", List.of()).run();
        AllocationService.Execution replay =
                service.execute(model, pools, "run-6", "op@epm", List.of(first));
        assertThat(replay.note()).contains("replay loads no new entries");
        assertThat(replay.run()).isEqualTo(first);
    }

    @Test
    void reversalIsALinkedCompensatingRunAndOriginalStaysImmutable() {
        AllocationService.Model model =
                new AllocationService.Model(
                        "alloc-7", List.of(step(1, "POOL-IT", "ENT-101", "55", "ENT-102", "45")));
        AllocationService.Run original =
                service.execute(
                                model,
                                new AllocationService.Pools(
                                        Map.of("POOL-IT", new BigDecimal("100.00"))),
                                "run-7",
                                "op@epm",
                                List.of())
                        .run();
        AllocationService.Execution reversal = service.reverse(original, "run-7-rev", "op@epm");
        assertThat(reversal.executed()).isTrue();
        assertThat(reversal.run().entries())
                .allSatisfy(
                        e -> {
                            assertThat(e.amount().signum()).isNegative();
                            assertThat(e.trace()).contains("reversal of run-7");
                        });
        // Original ledger unchanged; net effect is exactly zero.
        assertThat(original.entries()).hasSize(2);
        assertThat(
                        original.entries().stream()
                                .map(AllocationService.Entry::amount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .add(
                                        reversal.run().entries().stream()
                                                .map(AllocationService.Entry::amount)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .isEqualByComparingTo("0.00");
        assertThat(service.reverse(reversal.run(), "x", "op").note())
                .contains("cannot reverse a reversal");
    }
}
