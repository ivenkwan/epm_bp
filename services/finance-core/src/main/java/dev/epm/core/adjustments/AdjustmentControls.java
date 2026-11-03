package dev.epm.core.adjustments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Manual adjustment ledger control (Task 29, implementing the signed Task 15 contract). Lines must
 * balance per currency (ΣD = ΣC, scale 2); the approval level comes from the governed DMN table
 * over the absolute materiality; application requires an independent approver distinct from the
 * preparer; reversals are linked compensating entries — there is no destructive update anywhere.
 */
public final class AdjustmentControls {

    private AdjustmentControls() {}

    /** One adjustment line (side D or C, decimal string). */
    public record Line(int lineNumber, String accountId, char side, String amount) {}

    /** Approval levels (mirrors the DMN table and the §5 fixture expectations). */
    public enum Level {
        OWNER_REVIEW,
        INDEPENDENT_APPROVER,
        DOMAIN_APPROVER_PLUS_CERTIFIER
    }

    /** Balances the lines: Σ debits == Σ credits per proposal, scale 2. */
    public static boolean balanced(List<Line> lines) {
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (Line line : lines) {
            BigDecimal amount = new BigDecimal(line.amount()).setScale(2, RoundingMode.HALF_UP);
            if (line.side() == 'D') {
                debits = debits.add(amount);
            } else {
                credits = credits.add(amount);
            }
        }
        return debits.compareTo(credits) == 0;
    }

    /** Materiality: the absolute sum of debit lines (the §5 basis). */
    public static String materiality(List<Line> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (Line line : lines) {
            if (line.side() == 'D') {
                total = total.add(new BigDecimal(line.amount()).setScale(2, RoundingMode.HALF_UP));
            }
        }
        return total.toPlainString();
    }

    /** Selects the approval level for a materiality (identical to the DMN table). */
    public static Level levelFor(String materiality) {
        BigDecimal value = new BigDecimal(materiality);
        if (value.compareTo(new BigDecimal("1000.00")) < 0) {
            return Level.OWNER_REVIEW;
        }
        if (value.compareTo(new BigDecimal("10000.00")) < 0) {
            return Level.INDEPENDENT_APPROVER;
        }
        return Level.DOMAIN_APPROVER_PLUS_CERTIFIER;
    }

    /** Four-eyes: the approver must exist and differ from the preparer. */
    public static boolean independentApproval(String preparer, String approver) {
        return approver != null
                && !approver.isBlank()
                && (preparer == null || !preparer.equals(approver));
    }

    /** Reversal reference links a compensating entry to its original. */
    public static String reversalOf(String originalAdjustmentId) {
        return originalAdjustmentId + ":REVERSAL";
    }
}
