package dev.epm.planning.scenarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Task 48 acceptance (AC-F-028-01–02): scenarios are isolated copies; volume/price/FX overrides
 * stay in approved ranges; recalculation is deterministic and dependency-bounded; circular and
 * out-of-range inputs are refused without mutating baseline or last valid results; replay is
 * identical; promotion creates a new governed version, never an overwrite.
 */
class ScenarioIsolationTest {

    private final ScenarioService service = new ScenarioService();

    private static final List<ScenarioService.Driver> DRIVERS =
            List.of(
                    new ScenarioService.Driver(
                            "revenue",
                            "revenue = base × volume × price",
                            "drv-1.0.0",
                            new BigDecimal("0.50"),
                            new BigDecimal("2.00"),
                            List.of(),
                            "fpa@epm"),
                    new ScenarioService.Driver(
                            "fx",
                            "group translation factor",
                            "drv-1.0.0",
                            new BigDecimal("0.80"),
                            new BigDecimal("1.20"),
                            List.of(),
                            "treasury@epm"));

    private static ScenarioService.Baseline baseline() {
        return new ScenarioService.Baseline(
                "plan-bud-2027:r2",
                Map.of(
                        "revenue.base", new BigDecimal("1000.00"),
                        "fx.base", new BigDecimal("1.00")));
    }

    @Test
    void scenarioCopiesAreIsolatedFromTheBaseline() {
        ScenarioService.Scenario scenario = service.copyBaseline("sc-1", baseline());
        ScenarioService.Scenario overridden =
                service.override(
                                scenario,
                                new ScenarioService.Override(
                                        "revenue",
                                        ScenarioService.Override.Kind.VOLUME,
                                        new BigDecimal("1.10")),
                                DRIVERS)
                        .scenario();
        assertThat(overridden.overrides()).hasSize(1);
        // The baseline copy inside the scenario keeps its original values.
        assertThat(overridden.baselineValues().get("revenue.base")).isEqualByComparingTo("1000.00");
        assertThat(scenario.overrides()).as("source scenario untouched").isEmpty();
    }

    @Test
    void overridesRespectApprovedRangesAndKinds() {
        ScenarioService.Scenario scenario = service.copyBaseline("sc-2", baseline());
        ScenarioService.OverrideOutcome outOfRange =
                service.override(
                        scenario,
                        new ScenarioService.Override(
                                "revenue",
                                ScenarioService.Override.Kind.VOLUME,
                                new BigDecimal("5.00")),
                        DRIVERS);
        assertThat(outOfRange.accepted()).isFalse();
        assertThat(outOfRange.note()).contains("out-of-range").contains("approved [0.50, 2.00]");

        ScenarioService.OverrideOutcome unknown =
                service.override(
                        scenario,
                        new ScenarioService.Override(
                                "ghost", ScenarioService.Override.Kind.PRICE, BigDecimal.ONE),
                        DRIVERS);
        assertThat(unknown.accepted()).isFalse();
        assertThat(unknown.note()).contains("unknown driver");

        ScenarioService.OverrideOutcome volume =
                service.override(
                        scenario,
                        new ScenarioService.Override(
                                "revenue",
                                ScenarioService.Override.Kind.VOLUME,
                                new BigDecimal("1.50")),
                        DRIVERS);
        assertThat(volume.accepted()).isTrue();
        ScenarioService.OverrideOutcome price =
                service.override(
                        volume.scenario(),
                        new ScenarioService.Override(
                                "revenue",
                                ScenarioService.Override.Kind.PRICE,
                                new BigDecimal("1.20")),
                        DRIVERS);
        assertThat(price.accepted()).isTrue();
        assertThat(price.scenario().overrides()).hasSize(2);
    }

    @Test
    void dependentRecalculationIsDeterministicWithTraceableDifferences() {
        ScenarioService.Scenario scenario =
                service.override(
                                service.override(
                                                service.copyBaseline("sc-3", baseline()),
                                                new ScenarioService.Override(
                                                        "revenue",
                                                        ScenarioService.Override.Kind.VOLUME,
                                                        new BigDecimal("1.10")),
                                                DRIVERS)
                                        .scenario(),
                                new ScenarioService.Override(
                                        "fx",
                                        ScenarioService.Override.Kind.FX,
                                        new BigDecimal("1.05")),
                                DRIVERS)
                        .scenario();
        ScenarioService.Recalculation result = service.recalculate(scenario, DRIVERS, 5);
        assertThat(result.valid()).isTrue();
        // revenue.base 1000 × volume 1.10 × price (none) = 1100.00; fx 1.00 × 1.05 = 1.05.
        assertThat(result.scenario().resultValues().get("revenue.base"))
                .isEqualByComparingTo("1100.00");
        assertThat(result.scenario().resultValues().get("fx.base")).isEqualByComparingTo("1.05");
        assertThat(result.scenario().trace())
                .anyMatch(
                        t ->
                                t.contains("revenue.base")
                                        && t.contains("VOLUME 1.1")
                                        && t.contains("1100.00"));

        // Replay: identical results and trace.
        ScenarioService.Recalculation replay = service.recalculate(scenario, DRIVERS, 5);
        assertThat(replay.scenario().resultValues()).isEqualTo(result.scenario().resultValues());
        assertThat(replay.scenario().trace()).isEqualTo(result.scenario().trace());
    }

    @Test
    void circularDependenciesAreRefusedAndLastValidResultsPreserved() {
        List<ScenarioService.Driver> cyclic =
                List.of(
                        new ScenarioService.Driver(
                                "a",
                                "a depends on b",
                                "drv-1.0.0",
                                BigDecimal.ZERO,
                                BigDecimal.TEN,
                                List.of("b"),
                                "fpa@epm"),
                        new ScenarioService.Driver(
                                "b",
                                "b depends on a",
                                "drv-1.0.0",
                                BigDecimal.ZERO,
                                BigDecimal.TEN,
                                List.of("a"),
                                "fpa@epm"));
        ScenarioService.Scenario scenario = service.copyBaseline("sc-4", baseline());
        ScenarioService.Recalculation refused = service.recalculate(scenario, cyclic, 5);
        assertThat(refused.valid()).isFalse();
        assertThat(refused.note()).contains("circular dependency");
        assertThat(refused.scenario().status()).isEqualTo(ScenarioService.Scenario.Status.INVALID);
        assertThat(refused.scenario().resultValues())
                .as("invalid simulation leaves prior results untouched")
                .isEqualTo(scenario.resultValues());
    }

    @Test
    void depthBoundRefusesUnboundedChains() {
        List<ScenarioService.Driver> chain =
                List.of(
                        new ScenarioService.Driver(
                                "d1",
                                "leaf",
                                "drv-1.0.0",
                                BigDecimal.ZERO,
                                BigDecimal.TEN,
                                List.of(),
                                "fpa@epm"),
                        new ScenarioService.Driver(
                                "d2",
                                "→ d1",
                                "drv-1.0.0",
                                BigDecimal.ZERO,
                                BigDecimal.TEN,
                                List.of("d1"),
                                "fpa@epm"),
                        new ScenarioService.Driver(
                                "d3",
                                "→ d2",
                                "drv-1.0.0",
                                BigDecimal.ZERO,
                                BigDecimal.TEN,
                                List.of("d2"),
                                "fpa@epm"));
        ScenarioService.Scenario scenario = service.copyBaseline("sc-5", baseline());
        // Chain depth is 2 (d3 → d2 → d1): cap 1 refuses, cap 5 admits.
        assertThat(service.recalculate(scenario, chain, 1).valid()).isFalse();
        assertThat(service.recalculate(scenario, chain, 5).valid()).isTrue();
    }

    @Test
    void comparisonShowsSideBySideTraceableDifferences() {
        ScenarioService.Baseline base = baseline();
        ScenarioService.Scenario flat =
                service.recalculate(service.copyBaseline("sc-a", base), DRIVERS, 5).scenario();
        ScenarioService.Scenario raised =
                service.recalculate(
                                service.override(
                                                service.copyBaseline("sc-b", base),
                                                new ScenarioService.Override(
                                                        "revenue",
                                                        ScenarioService.Override.Kind.VOLUME,
                                                        new BigDecimal("1.20")),
                                                DRIVERS)
                                        .scenario(),
                                DRIVERS,
                                5)
                        .scenario();
        List<ScenarioService.Diff> diffs = service.compare(flat, raised);
        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).lineKey()).isEqualTo("revenue.base");
        assertThat(diffs.get(0).left()).isEqualByComparingTo("1000.00");
        assertThat(diffs.get(0).right()).isEqualByComparingTo("1200.00");
        assertThat(diffs.get(0).delta()).isEqualByComparingTo("200.00");
    }

    @Test
    void invalidSimulationNeverMutatesBaselineOrLastValidScenario() {
        ScenarioService.Baseline base = baseline();
        ScenarioService.Scenario scenario = service.copyBaseline("sc-6", base);
        ScenarioService.Recalculation valid = service.recalculate(scenario, DRIVERS, 5);
        // An out-of-range override is refused — the valid scenario keeps its results.
        ScenarioService.OverrideOutcome refused =
                service.override(
                        valid.scenario(),
                        new ScenarioService.Override(
                                "fx", ScenarioService.Override.Kind.FX, new BigDecimal("9.99")),
                        DRIVERS);
        assertThat(refused.accepted()).isFalse();
        assertThat(refused.scenario().status()).isEqualTo(ScenarioService.Scenario.Status.VALID);
        assertThat(base.values().get("revenue.base")).isEqualByComparingTo("1000.00");
    }

    @Test
    void promotionCreatesANewGovernedVersionNotAnOverwrite() {
        ScenarioService.Scenario scenario =
                service.recalculate(service.copyBaseline("sc-7", baseline()), DRIVERS, 5)
                        .scenario();
        ScenarioService.Promotion promoted =
                service.promote(scenario, "plan-bud-2027:r3", "approver@epm");
        assertThat(promoted.accepted()).isTrue();
        assertThat(promoted.newVersionRef()).isEqualTo("plan-bud-2027:r3");
        assertThat(promoted.promoted().status())
                .isEqualTo(ScenarioService.Scenario.Status.PROMOTED);
        assertThat(promoted.note()).contains("baseline and prior versions unchanged");
        // The original scenario object still records its own VALID state — nothing overwritten.
        assertThat(scenario.status()).isEqualTo(ScenarioService.Scenario.Status.VALID);

        assertThat(service.promote(scenario, "x", " ").accepted()).isFalse();
        ScenarioService.Scenario draft = service.copyBaseline("sc-8", baseline());
        assertThat(service.promote(draft, "x", "approver@epm").accepted()).isFalse();
    }
}
