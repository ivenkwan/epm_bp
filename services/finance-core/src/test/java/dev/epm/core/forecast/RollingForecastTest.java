package dev.epm.core.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 27 rolling forecast: baseline seeding, roll-forward exactly once, opening cash linked to the
 * prior certified close (non-reconciling rejected), period/category variance, zero horizon, and
 * preservation of the last approved version.
 */
@QuarkusTest
class RollingForecastTest {

    @Inject RollingForecastCalculator calculator;

    private static final LocalDate SEED = LocalDate.of(2026, 10, 31);
    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 30);

    private static RollingForecastCalculator.ForecastVersion baseline() {
        return new RollingForecastCalculator.ForecastVersion(
                "FC-2026-11",
                SEED,
                "1446.00",
                List.of(
                        new RollingForecastCalculator.ScheduleLine("2026-11", "500.00", "300.00"),
                        new RollingForecastCalculator.ScheduleLine("2026-12", "400.00", "250.00")));
    }

    @Test
    void rollForwardHappensExactlyOnceAndConsumeOnePeriod() {
        RollingForecastCalculator.RollResult first =
                calculator.rollForward(baseline(), "1446.00", NOVEMBER);
        assertThat(first.rejectedBecause()).isNull();
        assertThat(first.periods()).hasSize(1);
        RollingForecastCalculator.PeriodResult november = first.periods().get(0);
        assertThat(november.period()).isEqualTo("2026-11");
        assertThat(november.opening()).isEqualTo("1446.00");
        assertThat(november.closing()).isEqualTo("1646.00");

        // Rolling the same approved version again at the same point consumes nothing
        // further: the approved baseline is immutable and one period rolls exactly once.
        RollingForecastCalculator.RollResult repeat =
                calculator.rollForward(baseline(), "1446.00", NOVEMBER);
        assertThat(repeat.periods()).hasSize(1);
        assertThat(repeat.periods().get(0).period()).isEqualTo("2026-11");
    }

    @Test
    void nonReconcilingOpeningCashIsRejectedNotAssumed() {
        RollingForecastCalculator.RollResult mismatch =
                calculator.rollForward(baseline(), "1440.00", NOVEMBER);
        assertThat(mismatch.periods()).isEmpty();
        assertThat(mismatch.rejectedBecause()).contains("does not reconcile");
        assertThat(mismatch.rejectedBecause()).contains("1446.00");
        assertThat(mismatch.rejectedBecause()).contains("1440.00");
    }

    @Test
    void notYetDueLeavesTheApprovedVersionUntouched() {
        RollingForecastCalculator.RollResult early =
                calculator.rollForward(baseline(), "1446.00", LocalDate.of(2026, 11, 5));
        assertThat(early.periods()).isEmpty();
        assertThat(early.rejectedBecause()).isNull();
    }

    @Test
    void varianceComparesActualToForecastPerPeriod() {
        RollingForecastCalculator.RollResult rolled =
                calculator.rollForward(baseline(), "1446.00", NOVEMBER);
        List<String[]> variance =
                calculator.variance(
                        rolled.periods(),
                        List.of(
                                new String[] {"2026-11", "1650.00"},
                                new String[] {"2099-12", "1.00"}));
        assertThat(variance).hasSize(1);
        assertThat(variance.get(0)[0]).isEqualTo("2026-11");
        assertThat(variance.get(0)[1]).isEqualTo("1646.00");
        assertThat(variance.get(0)[2]).isEqualTo("1650.00");
        assertThat(variance.get(0)[3]).isEqualTo("4.00");
    }

    @Test
    void zeroHorizonAndEmptyScheduleAreGovernedRejections() {
        RollingForecastCalculator.ForecastVersion empty =
                new RollingForecastCalculator.ForecastVersion("FC-E", SEED, "1446.00", List.of());
        RollingForecastCalculator.RollResult rejected =
                calculator.rollForward(empty, "1446.00", NOVEMBER);
        assertThat(rejected.rejectedBecause()).contains("no schedule line");
    }
}
