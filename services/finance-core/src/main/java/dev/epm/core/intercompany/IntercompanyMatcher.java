package dev.epm.core.intercompany;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Intercompany loan matching (Task 25): deterministic pairing of counterparty sides (stable
 * counterparty ids, principal, currency, rate, tenor) under an absolute tolerance. Mismatches queue
 * with the difference and ageing visible; neither side is ever overwritten and nothing is
 * automatically eliminated.
 */
@ApplicationScoped
public class IntercompanyMatcher {

    /** Matcher version. */
    public static final String VERSION = "ic-1.0.0";

    /** Absolute principal tolerance (scale 2). */
    public static final String DEFAULT_TOLERANCE = "0.01";

    /** One loan side. */
    public record Loan(
            String loanId,
            String lenderEntityId,
            String borrowerEntityId,
            String principal,
            String currency,
            String rate,
            int tenorDays,
            java.time.LocalDate recordedDate) {}

    /** Match outcome for a pair attempt. */
    public record MatchResult(
            String leftLoanId,
            String rightLoanId,
            String principalDifference,
            String tolerance,
            String status) {}

    /** Attempts to match two sides under the tolerance. */
    public MatchResult match(Loan left, Loan right, String tolerance) {
        BigDecimal difference =
                new BigDecimal(left.principal())
                        .subtract(new BigDecimal(right.principal()))
                        .abs()
                        .setScale(2, RoundingMode.HALF_UP);
        boolean samePair =
                left.lenderEntityId().equals(right.borrowerEntityId())
                        && left.borrowerEntityId().equals(right.lenderEntityId());
        boolean sameTerms =
                left.currency().equals(right.currency())
                        && new BigDecimal(left.rate()).compareTo(new BigDecimal(right.rate())) == 0
                        && left.tenorDays() == right.tenorDays();
        String status;
        if (!samePair) {
            status = "MISMATCHED_COUNTERPARTY";
        } else if (!sameTerms) {
            status =
                    difference.compareTo(new BigDecimal(tolerance)) <= 0
                            ? "MISMATCHED_TERMS"
                            : "MISMATCHED_TERMS";
        } else if (difference.compareTo(new BigDecimal(tolerance)) <= 0) {
            status = "MATCHED";
        } else {
            status = "MISMATCHED_PRINCIPAL";
        }
        return new MatchResult(
                left.loanId(), right.loanId(), difference.toPlainString(), tolerance, status);
    }

    /** Pairs every loan with its best counterparty candidate (deterministic order). */
    public List<MatchResult> matchAll(List<Loan> loans, String tolerance) {
        List<MatchResult> results = new ArrayList<>();
        List<Loan> remaining = new ArrayList<>(loans);
        for (Loan left : loans) {
            remaining.remove(left);
            Loan best = null;
            for (Loan candidate : remaining) {
                if (candidate.lenderEntityId().equals(left.borrowerEntityId())
                        && candidate.borrowerEntityId().equals(left.lenderEntityId())) {
                    best = candidate;
                    break;
                }
            }
            if (best != null) {
                results.add(match(left, best, tolerance));
                remaining.remove(best);
            }
        }
        return results;
    }

    /** Ageing of a mismatch in days since the later recorded date. */
    public long ageingDays(MatchResult mismatch, List<Loan> loans) {
        java.time.LocalDate latest =
                loans.stream()
                        .filter(
                                loan ->
                                        loan.loanId().equals(mismatch.leftLoanId())
                                                || loan.loanId().equals(mismatch.rightLoanId()))
                        .map(Loan::recordedDate)
                        .max(java.time.LocalDate::compareTo)
                        .orElseThrow();
        return java.time.temporal.ChronoUnit.DAYS.between(latest, java.time.LocalDate.now());
    }
}
