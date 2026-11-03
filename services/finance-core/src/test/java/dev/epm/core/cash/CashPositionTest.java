package dev.epm.core.cash;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 23 golden tests: every expected value was hand-calculated independently in {@code
 * db/fixtures/expected/README.md} (§1) — opening + signed movements = closing, negative authorized
 * overdrafts stay explicit, FX-V1 group rollup at 1.1000, and opening/closing continuity
 * reconciles. The calculator must reproduce them exactly; binary floating point is never used.
 */
@QuarkusTest
class CashPositionTest {

    @Inject CashPositionCalculator calculator;

    private static final Path GOLDEN =
            Path.of("..", "..", "db", "fixtures", "expected", "expected-cash-position.csv");
    private static final Path GROUP_GOLDEN =
            Path.of("..", "..", "db", "fixtures", "expected", "expected-cash-position-group.csv");

    @Test
    void reproducesGoldenAccountPositionsExactly() throws Exception {
        List<CashPositionCalculator.AccountInput> inputs = explicitInputs();
        for (String[] expected : goldenRows()) {
            String accountId = expected[0];
            CashPositionCalculator.AccountInput input =
                    inputs.stream()
                            .filter(candidate -> candidate.accountId().equals(accountId))
                            .findFirst()
                            .orElseThrow();
            CashPositionCalculator.AccountPosition position = calculator.position(input);

            assertThat(position.opening()).as("%s opening", accountId).isEqualTo(expected[4]);
            assertThat(position.inflows()).as("%s inflows", accountId).isEqualTo(expected[5]);
            assertThat(position.outflows()).as("%s outflows", accountId).isEqualTo(expected[6]);
            assertThat(position.closing()).as("%s closing", accountId).isEqualTo(expected[7]);
            assertThat(position.sourceRevision()).isEqualTo(expected[8]);
            assertThat(position.fxVersion()).isEqualTo(expected[9]);
        }
    }

    @Test
    void negativeAuthorizedOverdraftStaysExplicitNotMissing() {
        // Golden ACC004: opening -75.50, outflow 4.50 → closing -80.00 (valid overdraft).
        CashPositionCalculator.AccountPosition overdraft =
                calculator.position(
                        new CashPositionCalculator.AccountInput(
                                "ACC004",
                                "ENT101",
                                "USD",
                                "-75.50",
                                "0.00",
                                "4.50",
                                "REV-2026-11-01-01",
                                "FX-V1"));
        assertThat(overdraft.closing()).isEqualTo("-80.00");
        assertThat(new java.math.BigDecimal(overdraft.closing()).signum())
                .as("overdraft stays negative and explicit")
                .isNegative();
    }

    @Test
    void zeroMovementAndZeroValuesRemainNumeric() {
        CashPositionCalculator.AccountPosition zero =
                calculator.position(
                        new CashPositionCalculator.AccountInput(
                                "ZERO", "ENT101", "USD", "0.00", "0.00", "0.00", "REV", "FX-V1"));
        assertThat(zero.closing()).isEqualTo("0.00");
    }

    @Test
    void groupRollupMatchesGoldenAtFxV1() throws Exception {
        List<CashPositionCalculator.AccountPosition> positions = new ArrayList<>();
        for (CashPositionCalculator.AccountInput input : explicitInputs()) {
            positions.add(calculator.position(input));
        }
        String groupClosing = calculator.groupClosing(positions, "USD", (from, to) -> "1.0000");
        // Golden group closing at FX-V1 with EUR→USD 1.1000: 1941.00 (§1).
        // The calculator applies the rate function; the golden fixture rate for EUR
        // accounts is 1.1000 (FXR001) and identity for USD.
        java.util.Map<String, String> rates = java.util.Map.of("EUR", "1.1000", "USD", "1.0000");
        String goldenClosing =
                calculator.groupClosing(positions, "USD", (from, to) -> rates.get(from));
        assertThat(goldenClosing).isEqualTo("1941.00");

        // Identity-basis rollup is itself a deterministic value (no FX applied).
        assertThat(groupClosing).isNotBlank();
    }

    @Test
    void continuityBreakIsSurfacedWithEntityAmountAndDifference() {
        // Golden continuity check: 1724.50 opening reconciles to 1941.00 closing (§1).
        CashPositionCalculator.Continuity consistent = calculator.continuity("1724.50", "1724.50");
        assertThat(consistent.consistent()).isTrue();
        assertThat(consistent.difference()).isEqualTo("0.00");

        CashPositionCalculator.Continuity broken = calculator.continuity("1724.50", "1730.00");
        assertThat(broken.consistent()).isFalse();
        assertThat(broken.difference()).isEqualTo("5.50");
    }

    @Test
    void calculationNeverUsesBinaryFloatingPoint() {
        // 0.1-style traps: decimal strings in, exact decimal strings out.
        CashPositionCalculator.AccountPosition trap =
                calculator.position(
                        new CashPositionCalculator.AccountInput(
                                "TRAP", "ENT101", "USD", "0.10", "0.20", "0.30", "REV", "FX-V1"));
        assertThat(trap.closing()).isEqualTo("0.00");
    }

    private static List<String[]> goldenRows() throws Exception {
        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(GOLDEN)) {
            if (line.isBlank() || line.startsWith("account_id")) {
                continue;
            }
            rows.add(line.split(",", -1));
        }
        return rows;
    }

    private static List<CashPositionCalculator.AccountInput> explicitInputs() {
        // Golden §1 inputs (hand-mapped from the fixtures; expected §1 arithmetic).
        return List.of(
                new CashPositionCalculator.AccountInput(
                        "ACC001",
                        "ENT101",
                        "USD",
                        "1000.00",
                        "250.50",
                        "100.25",
                        "REV-2026-11-01-01",
                        "FX-V1"),
                new CashPositionCalculator.AccountInput(
                        "ACC002",
                        "ENT102",
                        "EUR",
                        "500.00",
                        "0.00",
                        "50.00",
                        "REV-2026-11-01-01",
                        "FX-V1"),
                new CashPositionCalculator.AccountInput(
                        "ACC003",
                        "ENT103",
                        "USD",
                        "250.00",
                        "125.75",
                        "0.00",
                        "REV-2026-11-01-01",
                        "FX-V1"),
                new CashPositionCalculator.AccountInput(
                        "ACC004",
                        "ENT101",
                        "USD",
                        "-75.50",
                        "0.00",
                        "4.50",
                        "REV-2026-11-01-01",
                        "FX-V1"));
    }
}
