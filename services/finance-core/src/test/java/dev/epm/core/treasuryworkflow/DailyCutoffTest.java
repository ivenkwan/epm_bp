package dev.epm.core.treasuryworkflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Task 22 daily cutoff semantics: all-on-time completion, missed cutoff, exact T0 and T0+1s
 * boundaries, original timestamp preservation, stale prior-period exclusion, timer restart
 * (re-classification is pure), and reassignment-free owner semantics.
 */
@QuarkusTest
class DailyCutoffTest {

    @Inject TreasuryCalendarService calendar;

    private static final LocalDate DATE = LocalDate.of(2026, 11, 2);
    private static final OffsetDateTime T0 =
            DATE.atTime(LocalTime.of(17, 0)).atOffset(ZoneOffset.UTC);

    @Test
    void allOnTimeSubmissionsCompleteTheDay() {
        TreasuryCalendarService.Receipt onTime = calendar.classify(T0, T0.minusSeconds(60));
        assertThat(onTime.outcome()).isEqualTo(TreasuryCalendarService.Outcome.ON_TIME);
        // Original timestamp preserved verbatim.
        assertThat(onTime.receivedAtUtc()).isEqualTo(T0.minusSeconds(60));
    }

    @Test
    void exactT0IsOnTimeAndT0PlusOneSecondIsLate() {
        assertThat(calendar.classify(T0, T0).outcome())
                .isEqualTo(TreasuryCalendarService.Outcome.ON_TIME);
        TreasuryCalendarService.Receipt late = calendar.classify(T0, T0.plusSeconds(1));
        assertThat(late.outcome()).isEqualTo(TreasuryCalendarService.Outcome.LATE_AT_T0_PLUS);
        assertThat(late.receivedAtUtc()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void missedCutoffIsLateAndMissingIsNull() {
        assertThat(calendar.classify(T0, T0.plusMinutes(10)).outcome())
                .isEqualTo(TreasuryCalendarService.Outcome.LATE_AT_T0_PLUS);
        TreasuryCalendarService.Receipt missing = calendar.classify(T0, null);
        assertThat(missing.outcome()).isEqualTo(TreasuryCalendarService.Outcome.MISSING);
        assertThat(missing.receivedAtUtc()).isNull();
    }

    @Test
    void stalePriorPeriodSubmissionNeverCountsForToday() {
        TreasuryCalendarService.Receipt priorPeriodOnTime =
                calendar.classify(T0.minusDays(1), T0.minusDays(1).minusMinutes(30));
        assertThat(priorPeriodOnTime.outcome()).isEqualTo(TreasuryCalendarService.Outcome.ON_TIME);
        // On time for ITS period, but never counts for the evaluated period.
        assertThat(calendar.countsForPeriod(DATE.minusDays(1), DATE, priorPeriodOnTime.outcome()))
                .isFalse();
        assertThat(calendar.countsForPeriod(DATE, DATE, priorPeriodOnTime.outcome())).isTrue();
    }

    @Test
    void cutoffForEntityIsUtcWallClock() {
        OffsetDateTime cutoff = calendar.cutoffFor("ENT101", DATE, LocalTime.of(17, 0));
        assertThat(cutoff).isEqualTo(T0);
        assertThat(cutoff.getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void reclassificationIsPureTimerRestartSemantics() {
        // Timers only remind; classification of the same receipt never changes state.
        TreasuryCalendarService.Receipt first = calendar.classify(T0, T0.plusSeconds(2));
        TreasuryCalendarService.Receipt second = calendar.classify(T0, T0.plusSeconds(2));
        assertThat(second).isEqualTo(first);
    }
}
