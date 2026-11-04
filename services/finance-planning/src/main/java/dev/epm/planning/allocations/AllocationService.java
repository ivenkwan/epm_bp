package dev.epm.planning.allocations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task 49 traceable multi-step and step-down allocations (rule version alloc-1.0.0): steps execute
 * in approved sequence, each preserving the allocated total exactly (largest-remainder residual at
 * scale 2); step-down excludes senders of earlier steps from later receiving; every entry traces
 * source pool → receiver; runs are idempotent by run identity; reversal is a linked compensating
 * run that never overwrites the original ledger.
 */
public class AllocationService {

    public static final String RULE_VERSION = "alloc-1.0.0";

    /** One receiver weight inside a step. */
    public record Receiver(String receiver, BigDecimal driverValue) {}

    /** One allocation step (sender pool + weighted receivers). */
    public record Step(int stepNumber, String sender, List<Receiver> receivers) {}

    /** An approved model: ordered steps with step-down semantics. */
    public record Model(String modelId, List<Step> steps) {}

    /** Pool balances available before the run. */
    public record Pools(Map<String, BigDecimal> balances) {}

    /** One ledger entry with source-to-receiver trace. */
    public record Entry(
            int step, String sourcePool, String receiver, BigDecimal amount, String trace) {}

    /** A run: idempotent identity + immutable entries. */
    public record Run(String runId, String modelId, List<Entry> entries, boolean reversedOf) {}

    /** Execution outcome. */
    public record Execution(Run run, boolean executed, String note) {

        static Execution refused(String note) {
            return new Execution(null, false, note);
        }
    }

    /**
     * Validates and executes the model: sequence integrity, step-down exclusion, total
     * preservation, and explicit residual handling. Replaying the same runId against an existing
     * ledger returns the original run unchanged (idempotent).
     */
    public Execution execute(
            Model model, Pools pools, String runId, String executedBy, List<Run> existingRuns) {
        if (existingRuns.stream().anyMatch(r -> r.runId().equals(runId) && !r.reversedOf())) {
            return new Execution(
                    existingRuns.stream()
                            .filter(r -> r.runId().equals(runId))
                            .findFirst()
                            .orElseThrow(),
                    true,
                    "run " + runId + " already executed — replay loads no new entries");
        }
        if (model.steps().isEmpty()) {
            return Execution.refused("model has no steps");
        }
        for (int i = 0; i < model.steps().size(); i++) {
            Step step = model.steps().get(i);
            if (step.stepNumber() != i + 1) {
                return Execution.refused("invalid sequence: expected step " + (i + 1));
            }
            // A step-down sender may be a receiver of an earlier step rather than an
            // initial pool; anything else is an unknown source.
            boolean knownSender = pools.balances().containsKey(step.sender());
            if (!knownSender) {
                for (int earlier = 0; earlier < i; earlier++) {
                    if (model.steps().get(earlier).receivers().stream()
                            .anyMatch(r -> r.receiver().equals(step.sender()))) {
                        knownSender = true;
                        break;
                    }
                }
            }
            if (!knownSender) {
                return Execution.refused("unknown source pool " + step.sender());
            }
            if (step.receivers().isEmpty()) {
                return Execution.refused("step " + step.stepNumber() + " has no receivers");
            }
        }
        Map<String, BigDecimal> balances = new LinkedHashMap<>(pools.balances());
        List<String> alreadySent = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        for (Step step : model.steps()) {
            BigDecimal pool = balances.getOrDefault(step.sender(), BigDecimal.ZERO);
            List<Receiver> eligible =
                    step.receivers().stream()
                            .filter(
                                    r ->
                                            !alreadySent.contains(r.receiver())
                                                    && !r.receiver().equals(step.sender()))
                            .toList();
            if (pool.signum() == 0) {
                // A zero pool allocates nothing — legitimate, not an error.
                continue;
            }
            BigDecimal driverTotal =
                    eligible.stream()
                            .map(Receiver::driverValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (driverTotal.signum() <= 0) {
                return Execution.refused(
                        "step " + step.stepNumber() + " receivers have no positive driver total");
            }
            List<BigDecimal> amounts =
                    largestRemainder(pool, eligible.stream().map(Receiver::driverValue).toList());
            for (int i = 0; i < eligible.size(); i++) {
                Receiver receiver = eligible.get(i);
                BigDecimal amount = amounts.get(i);
                entries.add(
                        new Entry(
                                step.stepNumber(),
                                step.sender(),
                                receiver.receiver(),
                                amount,
                                "step "
                                        + step.stepNumber()
                                        + ": "
                                        + step.sender()
                                        + " → "
                                        + receiver.receiver()
                                        + " = "
                                        + pool.toPlainString()
                                        + " × "
                                        + receiver.driverValue().toPlainString()
                                        + "/"
                                        + driverTotal.toPlainString()));
                balances.merge(receiver.receiver(), amount, BigDecimal::add);
            }
            balances.put(step.sender(), BigDecimal.ZERO.setScale(2));
            alreadySent.add(step.sender());
        }
        return new Execution(
                new Run(runId, model.modelId(), entries, false),
                true,
                "allocated " + entries.size() + " entries under " + RULE_VERSION);
    }

    /**
     * Controlled reversal: a compensating run with negated amounts, linked to the original; the
     * original ledger entries are immutable and unchanged.
     */
    public Execution reverse(Run original, String reversalRunId, String executedBy) {
        if (original.reversedOf()) {
            return Execution.refused("cannot reverse a reversal");
        }
        List<Entry> compensating =
                original.entries().stream()
                        .map(
                                e ->
                                        new Entry(
                                                e.step(),
                                                e.sourcePool(),
                                                e.receiver(),
                                                e.amount().negate(),
                                                "reversal of "
                                                        + original.runId()
                                                        + ": "
                                                        + e.sourcePool()
                                                        + " → "
                                                        + e.receiver()))
                        .toList();
        return new Execution(
                new Run(reversalRunId, original.modelId(), compensating, true),
                true,
                "compensating run " + reversalRunId + " linked to " + original.runId);
    }

    /** Total allocated per step (for total-preservation checks). */
    public Map<Integer, BigDecimal> allocatedByStep(Run run) {
        Map<Integer, BigDecimal> totals = new LinkedHashMap<>();
        for (Entry entry : run.entries()) {
            totals.merge(entry.step(), entry.amount(), BigDecimal::add);
        }
        return totals;
    }

    /** Largest-remainder split preserving the pool total exactly at scale 2. */
    private static List<BigDecimal> largestRemainder(BigDecimal total, List<BigDecimal> drivers) {
        BigDecimal driverTotal = drivers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal[] floors = new BigDecimal[drivers.size()];
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < drivers.size(); i++) {
            floors[i] = total.multiply(drivers.get(i)).divide(driverTotal, 2, RoundingMode.DOWN);
            allocated = allocated.add(floors[i]);
        }
        BigDecimal residual = total.subtract(allocated);
        // Residual is whole cents at scale 2: assign them one by one to the largest
        // remainders in stable order until the pool total is preserved exactly.
        Integer[] order = new Integer[drivers.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        final List<BigDecimal> driversFinal = drivers;
        final BigDecimal[] floorsFinal = floors;
        final BigDecimal driverTotalFinal = driverTotal;
        java.util.Arrays.sort(
                order,
                (a, b) -> {
                    BigDecimal ra =
                            total.multiply(driversFinal.get(a))
                                    .subtract(floorsFinal[a].multiply(driverTotalFinal));
                    BigDecimal rb =
                            total.multiply(driversFinal.get(b))
                                    .subtract(floorsFinal[b].multiply(driverTotalFinal));
                    int cmp = rb.compareTo(ra);
                    return cmp != 0 ? cmp : Integer.compare(a, b);
                });
        int cents = residual.movePointRight(2).intValueExact();
        int idx = 0;
        BigDecimal cent = new BigDecimal("0.01");
        while (cents > 0) {
            int target = order[idx % order.length];
            floors[target] = floors[target].add(cent);
            cents--;
            idx++;
        }
        return List.of(floors);
    }
}
