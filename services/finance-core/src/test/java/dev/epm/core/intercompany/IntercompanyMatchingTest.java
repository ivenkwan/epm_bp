package dev.epm.core.intercompany;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 25 golden tests (§4, hand-calculated): matched pairs under the 0.01 tolerance; principal
 * beyond tolerance mismatches with the difference visible; no side is overwritten and no automatic
 * elimination happens.
 */
@QuarkusTest
class IntercompanyMatchingTest {

    @Inject IntercompanyMatcher matcher;

    private static final LocalDate DATE = LocalDate.of(2026, 11, 1);

    @Test
    void reproducesGoldenMatchOutcomes() {
        // Golden §4: ICL001↔ICL002 MATCHED (0.00 diff); ICL003↔ICL004
        // MISMATCHED_PRINCIPAL (5000.00 diff).
        IntercompanyMatcher.MatchResult matched =
                matcher.match(icl1(), icl2(), IntercompanyMatcher.DEFAULT_TOLERANCE);
        assertThat(matched.status()).isEqualTo("MATCHED");
        assertThat(matched.principalDifference()).isEqualTo("0.00");
        assertThat(matched.tolerance()).isEqualTo("0.01");

        IntercompanyMatcher.MatchResult mismatched =
                matcher.match(icl3(), icl4(), IntercompanyMatcher.DEFAULT_TOLERANCE);
        assertThat(mismatched.status()).isEqualTo("MISMATCHED_PRINCIPAL");
        assertThat(mismatched.principalDifference()).isEqualTo("5000.00");
    }

    @Test
    void exactToleranceBoundaryMatchesAndBeyondMismatches() {
        IntercompanyMatcher.Loan base =
                new IntercompanyMatcher.Loan(
                        "L1", "ENT101", "ENT102", "100.00", "USD", "0.0300", 90, DATE);
        IntercompanyMatcher.Loan exact =
                new IntercompanyMatcher.Loan(
                        "L2", "ENT102", "ENT101", "100.01", "USD", "0.0300", 90, DATE);
        IntercompanyMatcher.Loan over =
                new IntercompanyMatcher.Loan(
                        "L3", "ENT102", "ENT101", "100.02", "USD", "0.0300", 90, DATE);

        assertThat(matcher.match(base, exact, "0.01").status()).isEqualTo("MATCHED");
        assertThat(matcher.match(base, over, "0.01").status()).isEqualTo("MISMATCHED_PRINCIPAL");
        assertThat(matcher.match(base, over, "0.01").principalDifference()).isEqualTo("0.02");
    }

    @Test
    void currencyMismatchIsDistinctFromPrincipalMismatch() {
        IntercompanyMatcher.Loan usd =
                new IntercompanyMatcher.Loan(
                        "U1", "ENT101", "ENT102", "100.00", "USD", "0.0300", 90, DATE);
        IntercompanyMatcher.Loan eur =
                new IntercompanyMatcher.Loan(
                        "E1", "ENT102", "ENT101", "100.00", "EUR", "0.0300", 90, DATE);
        assertThat(matcher.match(usd, eur, "0.01").status()).isEqualTo("MISMATCHED_TERMS");
    }

    @Test
    void matchAllPairsCounterpartiesWithoutOverwritingSides() {
        List<IntercompanyMatcher.MatchResult> results =
                matcher.matchAll(
                        List.of(icl1(), icl2(), icl3(), icl4()),
                        IntercompanyMatcher.DEFAULT_TOLERANCE);
        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(IntercompanyMatcher.MatchResult::status)
                .containsExactlyInAnyOrder("MATCHED", "MISMATCHED_PRINCIPAL");
        // Both sides remain identifiable in every result (no side was consumed/overwritten).
        for (IntercompanyMatcher.MatchResult result : results) {
            assertThat(result.leftLoanId()).isNotEqualTo(result.rightLoanId());
        }
    }

    @Test
    void mismatchAgeingIsComputableFromTheRecordedDates() {
        IntercompanyMatcher.MatchResult mismatch =
                matcher.match(icl3(), icl4(), IntercompanyMatcher.DEFAULT_TOLERANCE);
        long ageing = matcher.ageingDays(mismatch, List.of(icl3(), icl4()));
        assertThat(ageing).isNotNegative();
    }

    private static IntercompanyMatcher.Loan icl1() {
        return new IntercompanyMatcher.Loan(
                "ICL001", "ENT101", "ENT102", "200000.00", "USD", "0.0325", 90, DATE);
    }

    private static IntercompanyMatcher.Loan icl2() {
        return new IntercompanyMatcher.Loan(
                "ICL002", "ENT102", "ENT101", "200000.00", "USD", "0.0325", 90, DATE);
    }

    private static IntercompanyMatcher.Loan icl3() {
        return new IntercompanyMatcher.Loan(
                "ICL003", "ENT101", "ENT103", "75000.00", "USD", "0.0400", 180, DATE);
    }

    private static IntercompanyMatcher.Loan icl4() {
        return new IntercompanyMatcher.Loan(
                "ICL004", "ENT103", "ENT101", "80000.00", "USD", "0.0400", 180, DATE);
    }
}
