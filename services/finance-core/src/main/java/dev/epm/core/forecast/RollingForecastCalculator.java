package dev.epm.core.forecast;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Configurable rolling cash forecast (Task 27). Forecast versions are immutable: a roll-forward
 * happens exactly once per period (the approved version is never mutated); opening cash ties to the
 * prior certified close (non-reconciling opening is a rejection, not an assumption); variance
 * compares actual to forecast by period.
 */
@ApplicationScoped
public class RollingForecastCalculator {

    /** Calculator version. */
    public static final String VERSION = "forecast-1.0.0";

    /** One period's direct receipt/payment schedule line (decimal strings). */
    public record ScheduleLine(String period, String receipts, String payments) {}

    /** An immutable forecast version. */
    public record ForecastVersion(
            String versionId, LocalDate seedDate, String openingCash, List<ScheduleLine> lines) {

        public ForecastVersion {
            lines = List.copyOf(lines);
        }
    }

    /** One calculated period: closing liquidity with visible basis. */
    public record PeriodResult(
            String period, String opening, String receipts, String payments, String closing) {}

    /** Roll-forward outcome: either the next period's results or a rejection. */
    public record RollResult(List<PeriodResult> periods, String rejectedBecause) {}

    /**
     * Rolls the forecast forward one period: derives the next period from the seed, consumes the
     * first schedule line exactly once, and links opening cash to the provided prior certified
     * close (mismatch → rejection, never an assumption).
     */
    public RollResult rollForward(
            ForecastVersion approved, String priorCertifiedClose, LocalDate currentDate) {
        if (!approved.openingCash().equals(priorCertifiedClose)) {
            return new RollResult(
                    List.of(),
                    "opening cash "
                            + approved.openingCash()
                            + " does not reconcile to the prior certified close "
                            + priorCertifiedClose);
        }
        LocalDate next = approved.seedDate().plusMonths(1);
        if (!next.isAfter(currentDate)) {
            // The next period is due: consume exactly one schedule line.
            if (approved.lines().isEmpty()) {
                return new RollResult(List.of(), "no schedule line for the next period");
            }
            ScheduleLine line = approved.lines().get(0);
            BigDecimal opening = money(approved.openingCash());
            BigDecimal receipts = money(line.receipts());
            BigDecimal payments = money(line.payments());
            BigDecimal closing =
                    opening.add(receipts).subtract(payments).setScale(2, RoundingMode.HALF_UP);
            return new RollResult(
                    List.of(
                            new PeriodResult(
                                    line.period(),
                                    opening.toPlainString(),
                                    receipts.toPlainString(),
                                    payments.toPlainString(),
                                    closing.toPlainString())),
                    null);
        }
        // Not yet due: the approved version stays untouched.
        return new RollResult(List.of(), null);
    }

    /** Actual-versus-forecast variance per period (decimal strings, scale 2). */
    public List<String[]> variance(List<PeriodResult> forecast, List<String[]> actuals) {
        List<String[]> rows = new ArrayList<>();
        for (String[] actual : actuals) {
            String period = actual[0];
            PeriodResult match =
                    forecast.stream()
                            .filter(row -> row.period().equals(period))
                            .findFirst()
                            .orElse(null);
            if (match == null) {
                continue;
            }
            BigDecimal variance =
                    money(actual[1])
                            .subtract(money(match.closing()))
                            .setScale(2, RoundingMode.HALF_UP);
            rows.add(new String[] {period, match.closing(), actual[1], variance.toPlainString()});
        }
        return rows;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }
}
