package dev.epm.tests.migration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.epm.integration.migration.MigrationRunner;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 33 migration replay: control totals exact, out-of-tolerance opening balance blocks,
 * exact-cutoff record included at T0 and excluded at T0+1s, duplicate replay loads zero new facts,
 * and the fallback stays active after any failure.
 */
class MigrationReplayTest {

    private static final String CUTOFF = "2026-10-31T23:59:59Z";

    @Test
    void controlTotalsMustMatchExactly() {
        // Golden MIG001: 3 rows, 5250.75 total, inclusive cutoff.
        List<MigrationRunner.LegacyRecord> records =
                List.of(
                        new MigrationRunner.LegacyRecord("MIG001-1", CUTOFF, "1750.25"),
                        new MigrationRunner.LegacyRecord(
                                "MIG001-2", "2026-11-01T00:00:00Z", "1750.25"),
                        new MigrationRunner.LegacyRecord(
                                "MIG001-3", "2026-10-15T12:00:00Z", "1750.25"));
        MigrationRunner.BatchOutcome ok =
                MigrationRunner.load(
                        "MIG001", "GL_ENTRY", records, CUTOFF, true, 2, "3500.50", List.of());
        assertThat(ok.outcome()).isEqualTo("LOADED");
        assertThat(ok.rowsLoaded()).isEqualTo(2);
        assertThat(ok.totalAmount()).isEqualTo("3500.50");

        // Wrong expected total: governed refusal, legacy path stays authoritative.
        MigrationRunner.BatchOutcome blocked =
                MigrationRunner.load(
                        "MIG001-bad", "GL_ENTRY", records, CUTOFF, true, 2, "9999.00", List.of());
        assertThat(blocked.outcome()).isEqualTo("BLOCKED_TOLERANCE");
    }

    @Test
    void outOfToleranceOpeningBalanceBlocks() {
        // Golden MIG002 expectation: 1250000.00 expected vs 999000.00 held.
        String verdict = MigrationRunner.openingBalanceGate("1250000.00", "999000.00");
        assertThat(verdict).startsWith("OUT_OF_TOLERANCE_BLOCKER");
        assertThat(verdict).contains("1250000.00").contains("999000.00");
        assertThat(MigrationRunner.openingBalanceGate("1250.00", "1250.00")).isEqualTo("OK");
        // Within the 0.01 tolerance passes.
        assertThat(MigrationRunner.openingBalanceGate("1250.00", "1250.01")).isEqualTo("OK");
    }

    @Test
    void exactCutoffRecordIncludedAndT0PlusOneExcluded() {
        MigrationRunner.LegacyRecord atT0 =
                new MigrationRunner.LegacyRecord("CUT-1", CUTOFF, "10.00");
        MigrationRunner.LegacyRecord atT0Plus1 =
                new MigrationRunner.LegacyRecord("CUT-2", "2026-11-01T00:00:00Z", "10.00");
        MigrationRunner.BatchOutcome inclusive =
                MigrationRunner.load(
                        "CUT-A", "GL_ENTRY", List.of(atT0), CUTOFF, true, 1, "10.00", List.of());
        assertThat(inclusive.outcome()).isEqualTo("LOADED");
        MigrationRunner.BatchOutcome excluded =
                MigrationRunner.load(
                        "CUT-B",
                        "GL_ENTRY",
                        List.of(atT0Plus1),
                        CUTOFF,
                        true,
                        1,
                        "10.00",
                        List.of());
        assertThat(excluded.outcome())
                .as("T0+1s record is out of the inclusive cutoff and the totals fail")
                .isEqualTo("BLOCKED_TOLERANCE");
    }

    @Test
    void duplicateReplayLoadsZeroNewFacts() {
        List<MigrationRunner.LegacyRecord> records =
                List.of(
                        new MigrationRunner.LegacyRecord(
                                "DUP-1", "2026-10-01T00:00:00Z", "100.00"));
        MigrationRunner.BatchOutcome replay =
                MigrationRunner.load(
                        "MIG-R", "GL_ENTRY", records, CUTOFF, true, 0, "0.00", List.of("DUP-1"));
        assertThat(replay.outcome()).isEqualTo("LOADED");
        assertThat(replay.rowsLoaded()).as("replayed records load no new facts").isZero();
        assertThat(replay.totalAmount()).isEqualTo("0.00");
    }

    @Test
    void fallbackStaysActiveUntilAnExplicitGo() {
        assertThat(MigrationRunner.fallbackActive("REGISTERED")).isTrue();
        assertThat(MigrationRunner.fallbackActive("LOADED")).isTrue();
        assertThat(MigrationRunner.fallbackActive("NO_GO")).isTrue();
        assertThat(MigrationRunner.fallbackActive("GO")).isFalse();
    }
}
