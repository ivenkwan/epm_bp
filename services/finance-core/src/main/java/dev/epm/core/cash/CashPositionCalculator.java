package dev.epm.core.cash;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Certified cash position and direct cash flow (Task 23). Fixed-precision decimal arithmetic only
 * (BigDecimal, scale 2, HALF_UP): closing = opening + inflows − outflows per account; group totals
 * roll up with an explicit FX basis. Continuity (prior close → today's opening) is checked and
 * breaks are surfaced, never netted.
 */
@ApplicationScoped
public class CashPositionCalculator {

    /** Calculator version (golden expectations pinned against this). */
    public static final String VERSION = "cash-1.0.0";

    /** One account position: the calculator's input unit. */
    public record AccountInput(
            String accountId,
            String entityId,
            String currency,
            String opening,
            String inflows,
            String outflows,
            String sourceRevision,
            String fxVersion) {}

    /** Calculated account position with visible basis. */
    public record AccountPosition(
            String accountId,
            String entityId,
            String currency,
            String opening,
            String inflows,
            String outflows,
            String closing,
            String sourceRevision,
            String fxVersion) {}

    /** Continuity check outcome between a prior certified close and today's opening. */
    public record Continuity(
            boolean consistent, String priorClose, String opening, String difference) {}

    /** Calculates one account position (scale 2, HALF_UP, decimal strings in/out). */
    public AccountPosition position(AccountInput input) {
        java.math.BigDecimal opening = money(input.opening());
        java.math.BigDecimal inflows = money(input.inflows());
        java.math.BigDecimal outflows = money(input.outflows());
        java.math.BigDecimal closing =
                opening.add(inflows).subtract(outflows).setScale(2, java.math.RoundingMode.HALF_UP);
        return new AccountPosition(
                input.accountId(),
                input.entityId(),
                input.currency(),
                opening.toPlainString(),
                inflows.toPlainString(),
                outflows.toPlainString(),
                closing.toPlainString(),
                input.sourceRevision(),
                input.fxVersion());
    }

    /**
     * Group rollup at an FX basis: converts each account closing to the group currency with the
     * given rate (identity 1 for same currency), scale 2 HALF_UP.
     */
    public String groupClosing(
            java.util.List<AccountPosition> positions,
            String groupCurrency,
            java.util.function.BiFunction<String, String, String> fxRate) {
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (AccountPosition position : positions) {
            java.math.BigDecimal rate =
                    new java.math.BigDecimal(fxRate.apply(position.currency(), groupCurrency))
                            .setScale(4, java.math.RoundingMode.HALF_UP);
            java.math.BigDecimal converted =
                    new java.math.BigDecimal(position.closing())
                            .multiply(rate)
                            .setScale(2, java.math.RoundingMode.HALF_UP);
            total = total.add(converted);
        }
        return total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** Continuity: prior certified close must equal today's opening exactly. */
    public Continuity continuity(String priorClose, String opening) {
        java.math.BigDecimal prior = money(priorClose);
        java.math.BigDecimal today = money(opening);
        java.math.BigDecimal difference = today.subtract(prior);
        return new Continuity(
                difference.signum() == 0,
                prior.toPlainString(),
                today.toPlainString(),
                difference.toPlainString());
    }

    private static java.math.BigDecimal money(String value) {
        return new java.math.BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
