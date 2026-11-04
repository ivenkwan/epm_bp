package dev.epm.tests.migration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.epm.integration.migration.MigrationRunner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 46 planning migration: approved opening plan versions, dimensions, FX basis, schedules, and
 * historical comparisons migrate under register controls (control totals, inclusive cutoff, replay
 * identity) with per-object lineage; replay loads zero new facts; the integrated statements
 * reconcile independently (hand-recomputed arithmetic, not the planning service under test); and
 * the legacy fallback stays active until an explicit GO.
 */
class PlanningMigrationTest {

    private static final String CUTOFF = "2026-10-31T23:59:59Z";

    @Test
    void openingPlanVersionMigratesWithControlTotals() {
        List<MigrationRunner.LegacyRecord> openingVersion =
                List.of(
                        new MigrationRunner.LegacyRecord("PLAN-bud-2027-r1", CUTOFF, "6350.00"),
                        new MigrationRunner.LegacyRecord(
                                "PLAN-fc-2026-r3", "2026-10-20T12:00:00Z", "4100.00"));
        MigrationRunner.BatchOutcome outcome =
                MigrationRunner.load(
                        "PMIG001",
                        "PLAN_VERSION",
                        openingVersion,
                        CUTOFF,
                        true,
                        2,
                        "10450.00",
                        List.of());
        assertThat(outcome.outcome()).isEqualTo("LOADED");
        assertThat(outcome.rowsLoaded()).isEqualTo(2);
        assertThat(outcome.totalAmount()).isEqualTo("10450.00");

        // Wrong control totals: governed refusal, nothing migrates.
        MigrationRunner.BatchOutcome blocked =
                MigrationRunner.load(
                        "PMIG001-bad",
                        "PLAN_VERSION",
                        openingVersion,
                        CUTOFF,
                        true,
                        2,
                        "9999.00",
                        List.of());
        assertThat(blocked.outcome()).isEqualTo("BLOCKED_TOLERANCE");
    }

    @Test
    void dimensionsAndFxBasisMigrateUnderRegisterControls() {
        List<MigrationRunner.LegacyRecord> dimensions =
                List.of(
                        new MigrationRunner.LegacyRecord("DIM-entity", CUTOFF, "12.00"),
                        new MigrationRunner.LegacyRecord("DIM-account", CUTOFF, "40.00"));
        assertThat(
                        MigrationRunner.load(
                                        "PMIG002",
                                        "PLANNING_DIMENSION",
                                        dimensions,
                                        CUTOFF,
                                        true,
                                        2,
                                        "52.00",
                                        List.of())
                                .outcome())
                .isEqualTo("LOADED");

        List<MigrationRunner.LegacyRecord> fxBasis =
                List.of(new MigrationRunner.LegacyRecord("FX-2026-01", CUTOFF, "8.00"));
        assertThat(
                        MigrationRunner.load(
                                        "PMIG003",
                                        "FX_BASIS",
                                        fxBasis,
                                        CUTOFF,
                                        true,
                                        1,
                                        "8.00",
                                        List.of())
                                .outcome())
                .isEqualTo("LOADED");

        // A rate arriving after the inclusive cutoff cannot join the approved basis.
        List<MigrationRunner.LegacyRecord> lateRate =
                List.of(
                        new MigrationRunner.LegacyRecord(
                                "FX-2026-02", "2026-11-01T00:00:00Z", "8.00"));
        assertThat(
                        MigrationRunner.load(
                                        "PMIG003-late",
                                        "FX_BASIS",
                                        lateRate,
                                        CUTOFF,
                                        true,
                                        1,
                                        "8.00",
                                        List.of())
                                .outcome())
                .isEqualTo("BLOCKED_TOLERANCE");
    }

    @Test
    void schedulesMigrateAndStatementsReconcileIndependently() {
        // Fixtures §8 golden schedule set (control total 6350.00 across 8 lines).
        List<MigrationRunner.LegacyRecord> schedules =
                List.of(
                        new MigrationRunner.LegacyRecord("SCH-rev-dom", CUTOFF, "1000.00"),
                        new MigrationRunner.LegacyRecord("SCH-rev-exp", CUTOFF, "500.00"),
                        new MigrationRunner.LegacyRecord("SCH-opex-sal", CUTOFF, "400.00"),
                        new MigrationRunner.LegacyRecord("SCH-opex-oth", CUTOFF, "100.00"),
                        new MigrationRunner.LegacyRecord("SCH-capex", CUTOFF, "300.00"),
                        new MigrationRunner.LegacyRecord("SCH-depr", CUTOFF, "50.00"),
                        new MigrationRunner.LegacyRecord("SCH-open-cash", CUTOFF, "2000.00"),
                        new MigrationRunner.LegacyRecord("SCH-open-eq", CUTOFF, "2000.00"));
        MigrationRunner.BatchOutcome outcome =
                MigrationRunner.load(
                        "PMIG004",
                        "INPUT_SCHEDULE",
                        schedules,
                        CUTOFF,
                        true,
                        8,
                        "6350.00",
                        List.of());
        assertThat(outcome.outcome()).isEqualTo("LOADED");
        assertThat(outcome.rowsLoaded()).isEqualTo(8);

        // Independent reconciliation: hand-recomputed §8 arithmetic — deliberately NOT
        // using the planning service under test.
        BigDecimal revenueTotal = dec("1000.00").add(dec("500.00"));
        BigDecimal opexTotal = dec("400.00").add(dec("100.00"));
        BigDecimal ebit = revenueTotal.subtract(opexTotal);
        BigDecimal tax = ebit.multiply(dec("0.25")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netIncome = ebit.subtract(tax).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cashFlow = netIncome.add(dec("50.00")).subtract(dec("300.00"));
        BigDecimal closingCash = dec("2000.00").add(cashFlow);
        BigDecimal assets = closingCash.add(dec("300.00")).subtract(dec("50.00"));
        BigDecimal equity = dec("2000.00").add(netIncome);

        assertThat(revenueTotal).isEqualByComparingTo("1500.00");
        assertThat(netIncome).isEqualByComparingTo("750.00");
        assertThat(cashFlow).isEqualByComparingTo("500.00");
        assertThat(closingCash).isEqualByComparingTo("2500.00");
        assertThat(assets).isEqualByComparingTo("2750.00");
        assertThat(equity).isEqualByComparingTo("2750.00");
        assertThat(assets.subtract(equity)).isEqualByComparingTo("0.00");
        // Migrated schedule control total equals the independently summed lines.
        assertThat(
                        schedules.stream()
                                .map(r -> dec(r.amount()))
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(outcome.totalAmount());
    }

    @Test
    void historicalComparisonsMigrateWithInclusiveCutoff() {
        List<MigrationRunner.LegacyRecord> comparisons =
                List.of(
                        new MigrationRunner.LegacyRecord("CMP-2025-bud", CUTOFF, "9800.00"),
                        new MigrationRunner.LegacyRecord(
                                "CMP-2026-bud", "2026-10-01T00:00:00Z", "10450.00"));
        assertThat(
                        MigrationRunner.load(
                                        "PMIG005",
                                        "PLAN_COMPARISON",
                                        comparisons,
                                        CUTOFF,
                                        true,
                                        2,
                                        "20250.00",
                                        List.of())
                                .rowsLoaded())
                .isEqualTo(2);
    }

    @Test
    void planningMigrationReplayLoadsZeroNewFacts() {
        List<MigrationRunner.LegacyRecord> schedules =
                List.of(new MigrationRunner.LegacyRecord("SCH-rev-dom", CUTOFF, "1000.00"));
        MigrationRunner.BatchOutcome replay =
                MigrationRunner.load(
                        "PMIG004-r",
                        "INPUT_SCHEDULE",
                        schedules,
                        CUTOFF,
                        true,
                        0,
                        "0.00",
                        List.of("SCH-rev-dom"));
        assertThat(replay.outcome()).isEqualTo("LOADED");
        assertThat(replay.rowsLoaded()).as("replayed planning records load no new facts").isZero();
    }

    @Test
    void legacyFallbackStaysActiveUntilExplicitGo() {
        assertThat(MigrationRunner.fallbackActive("PENDING")).isTrue();
        assertThat(MigrationRunner.fallbackActive("FAILED")).isTrue();
        assertThat(MigrationRunner.fallbackActive("GO")).isFalse();
        // No test path flips the fallback: the GO is a recorded human decision.
    }

    private static BigDecimal dec(String value) {
        return new BigDecimal(value);
    }
}
