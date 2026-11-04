package dev.epm.planning.scenarios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task 48 approved drivers and isolated what-if scenarios: drivers carry approved ranges, versioned
 * formulas, dependencies, and owners; scenarios are immutable copies of a baseline with
 * volume/price/FX overrides. Recalculation is deterministic, dependency-validated (cycles refused),
 * and bounded; invalid simulations never mutate the baseline or the last valid scenario. Promotion
 * creates a new governed version — never an in-place overwrite.
 */
public class ScenarioService {

    /** One approved driver (from {@code epm_driver}). */
    public record Driver(
            String driverId,
            String formula,
            String formulaVersion,
            BigDecimal minValue,
            BigDecimal maxValue,
            List<String> dependsOn,
            String owner) {}

    /** An override applied inside a scenario. */
    public record Override(String driverId, Kind kind, BigDecimal value) {

        public enum Kind {
            VOLUME,
            PRICE,
            FX
        }
    }

    /** A scenario: an immutable copy of a baseline plus overrides and results. */
    public record Scenario(
            String scenarioId,
            String baselineRef,
            List<Override> overrides,
            Map<String, BigDecimal> baselineValues,
            Map<String, BigDecimal> resultValues,
            List<String> trace,
            Status status) {

        public enum Status {
            DRAFT,
            VALID,
            INVALID,
            PROMOTED
        }
    }

    /** Baseline values keyed by driver line (e.g. "revenue.base"). */
    public record Baseline(String ref, Map<String, BigDecimal> values) {}

    /** Outcome of an override attempt. */
    public record OverrideOutcome(Scenario scenario, boolean accepted, String note) {}

    /** Copies the baseline into a new DRAFT scenario (baseline untouched). */
    public Scenario copyBaseline(String scenarioId, Baseline baseline) {
        return new Scenario(
                scenarioId,
                baseline.ref(),
                new ArrayList<>(),
                new LinkedHashMap<>(baseline.values()),
                new LinkedHashMap<>(),
                new ArrayList<>(),
                Scenario.Status.DRAFT);
    }

    /**
     * Applies a volume/price/FX override inside the approved range; out-of-range or unknown drivers
     * are refused without touching the scenario.
     */
    public OverrideOutcome override(Scenario scenario, Override override, List<Driver> drivers) {
        Driver driver =
                drivers.stream()
                        .filter(d -> d.driverId().equals(override.driverId()))
                        .findFirst()
                        .orElse(null);
        if (driver == null) {
            return new OverrideOutcome(scenario, false, "unknown driver " + override.driverId());
        }
        if (override.value().compareTo(driver.minValue()) < 0
                || override.value().compareTo(driver.maxValue()) > 0) {
            return new OverrideOutcome(
                    scenario,
                    false,
                    "out-of-range driver value "
                            + override.value()
                            + " for "
                            + override.driverId()
                            + " (approved ["
                            + driver.minValue()
                            + ", "
                            + driver.maxValue()
                            + "])");
        }
        List<Override> overrides = new ArrayList<>(scenario.overrides());
        overrides.removeIf(
                o -> o.driverId().equals(override.driverId()) && o.kind() == override.kind());
        overrides.add(override);
        Scenario updated =
                new Scenario(
                        scenario.scenarioId(),
                        scenario.baselineRef(),
                        overrides,
                        scenario.baselineValues(),
                        scenario.resultValues(),
                        scenario.trace(),
                        Scenario.Status.DRAFT);
        return new OverrideOutcome(updated, true, "override accepted");
    }

    /** Recalculation outcome. */
    public record Recalculation(Scenario scenario, boolean valid, String note) {}

    /**
     * Deterministic recalculation: dependency graph validated (cycles refused), evaluation bounded
     * by the declared depth cap. On failure the last valid results are preserved untouched. Formula
     * (rule version drv-1.0.0): each line equals its baseline multiplied by VOLUME × PRICE × FX
     * override factors when present.
     */
    public static final String SCENARIO_RULE_VERSION = "drv-1.0.0";

    public Recalculation recalculate(Scenario scenario, List<Driver> drivers, int depthCap) {
        String cycle = findCycle(drivers);
        if (cycle != null) {
            return new Recalculation(
                    invalid(scenario),
                    false,
                    "circular dependency through " + cycle + " — simulation refused");
        }
        Map<String, Integer> depth = new HashMap<>();
        for (Driver driver : drivers) {
            depth.put(driver.driverId(), depthOf(driver, drivers, depthCap));
        }
        for (Integer d : depth.values()) {
            if (d > depthCap) {
                return new Recalculation(
                        invalid(scenario),
                        false,
                        "dependency depth exceeds the evaluation bound " + depthCap);
            }
        }
        Map<String, BigDecimal> factors = new LinkedHashMap<>();
        for (Override o : scenario.overrides()) {
            factors.merge(
                    o.driverId(),
                    o.value(),
                    (a, b) -> a.multiply(b).setScale(6, RoundingMode.HALF_UP));
        }
        Map<String, BigDecimal> results = new LinkedHashMap<>();
        List<String> trace = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : scenario.baselineValues().entrySet()) {
            BigDecimal value = entry.getValue();
            StringBuilder t =
                    new StringBuilder(
                            entry.getKey() + " = " + value.stripTrailingZeros().toPlainString());
            for (Override o : scenario.overrides()) {
                if (entry.getKey().startsWith(o.driverId())) {
                    value = value.multiply(o.value()).setScale(2, RoundingMode.HALF_UP);
                    t.append(" × ")
                            .append(o.kind())
                            .append(' ')
                            .append(o.value().stripTrailingZeros().toPlainString());
                }
            }
            BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
            results.put(entry.getKey(), scaled);
            trace.add(t.append(" = ").append(scaled.toPlainString()).toString());
        }
        Scenario valid =
                new Scenario(
                        scenario.scenarioId(),
                        scenario.baselineRef(),
                        scenario.overrides(),
                        scenario.baselineValues(),
                        results,
                        trace,
                        Scenario.Status.VALID);
        return new Recalculation(valid, true, "recalculated under " + SCENARIO_RULE_VERSION);
    }

    /** Side-by-side traceable difference between two scenarios. */
    public record Diff(String lineKey, BigDecimal left, BigDecimal right, BigDecimal delta) {}

    public List<Diff> compare(Scenario left, Scenario right) {
        List<Diff> diffs = new ArrayList<>();
        for (String key : left.resultValues().keySet()) {
            BigDecimal l = left.resultValues().get(key);
            BigDecimal r = right.resultValues().get(key);
            if (r == null || l.compareTo(r) != 0) {
                diffs.add(
                        new Diff(
                                key,
                                l,
                                r,
                                r == null
                                        ? null
                                        : r.subtract(l).setScale(2, RoundingMode.HALF_UP)));
            }
        }
        return diffs;
    }

    /** Promotion outcome: a new governed version reference, scenario never overwritten. */
    public record Promotion(
            Scenario promoted, String newVersionRef, boolean accepted, String note) {}

    public Promotion promote(Scenario scenario, String newVersionRef, String approver) {
        if (scenario.status() != Scenario.Status.VALID) {
            return new Promotion(scenario, null, false, "only a VALID scenario can be promoted");
        }
        if (approver == null || approver.isBlank()) {
            return new Promotion(scenario, null, false, "promotion requires a named approver");
        }
        Scenario promoted =
                new Scenario(
                        scenario.scenarioId(),
                        scenario.baselineRef(),
                        scenario.overrides(),
                        scenario.baselineValues(),
                        scenario.resultValues(),
                        scenario.trace(),
                        Scenario.Status.PROMOTED);
        return new Promotion(
                promoted,
                newVersionRef,
                true,
                "promoted as new governed version "
                        + newVersionRef
                        + " by "
                        + approver
                        + " — baseline and prior versions unchanged");
    }

    private static Scenario invalid(Scenario scenario) {
        return new Scenario(
                scenario.scenarioId(),
                scenario.baselineRef(),
                scenario.overrides(),
                scenario.baselineValues(),
                scenario.resultValues(),
                scenario.trace(),
                Scenario.Status.INVALID);
    }

    private static String findCycle(List<Driver> drivers) {
        Map<String, List<String>> edges = new HashMap<>();
        for (Driver d : drivers) {
            edges.put(d.driverId(), d.dependsOn());
        }
        for (String start : edges.keySet()) {
            Deque<String> stack = new ArrayDeque<>();
            if (visit(start, edges, stack)) {
                return start;
            }
        }
        return null;
    }

    private static boolean visit(
            String node, Map<String, List<String>> edges, Deque<String> stack) {
        if (stack.contains(node)) {
            return true;
        }
        stack.push(node);
        for (String next : edges.getOrDefault(node, List.of())) {
            if (edges.containsKey(next) && visit(next, edges, stack)) {
                return true;
            }
        }
        stack.pop();
        return false;
    }

    private static int depthOf(Driver driver, List<Driver> drivers, int cap) {
        return depthOf(driver.driverId(), drivers, new HashMap<>(), cap, 0);
    }

    private static int depthOf(
            String nodeId, List<Driver> drivers, Map<String, Integer> memo, int cap, int walked) {
        if (walked > cap) {
            return cap + 1;
        }
        Driver driver =
                drivers.stream().filter(d -> d.driverId().equals(nodeId)).findFirst().orElse(null);
        if (driver == null || driver.dependsOn().isEmpty()) {
            return 0;
        }
        if (memo.containsKey(nodeId)) {
            return memo.get(nodeId);
        }
        int max = 0;
        for (String dep : driver.dependsOn()) {
            max = Math.max(max, 1 + depthOf(dep, drivers, memo, cap, walked + 1));
        }
        memo.put(nodeId, max);
        return max;
    }
}
