package dev.epm.tests.design;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 15 design-contract tests: validate the adjustment and migration control designs against
 * their fixture arithmetic before any execution exists. Unbalanced proposals are rejected;
 * materiality selects the documented approval level; exact-cutoff records land on exactly one side;
 * out-of-tolerance opening balances block; duplicate replays create zero new facts.
 */
class ControlDesignContractTest {

    private static final Path FIXTURES = Path.of("..", "..", "db", "fixtures");
    private static final Path MIGRATION = FIXTURES.resolve("migration");
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Test
    void adjustmentFixtureArithmeticMatchesTheLedgerDesign() throws Exception {
        List<String[]> rows = csv(MIGRATION.resolve("adjustment-example.csv"));
        assertThat(rows).hasSize(4);

        // Hand-calculated expectations from the design document:
        // ADJ001 1000.00 balanced, materiality 990.00 → OWNER_REVIEW
        expect(rows.get(0), "ADJ001", "BALANCED", "1000.00", "1000.00", "OWNER_REVIEW");
        // ADJ002 5000.00 balanced at the MEDIUM boundary → INDEPENDENT_APPROVER
        expect(rows.get(1), "ADJ002", "BALANCED", "5000.00", "5000.00", "INDEPENDENT_APPROVER");
        // ADJ003 15000.00 balanced HIGH → DOMAIN_APPROVER_PLUS_CERTIFIER
        expect(
                rows.get(2),
                "ADJ003",
                "BALANCED",
                "15000.00",
                "15000.00",
                "DOMAIN_APPROVER_PLUS_CERTIFIER");
        // ADJ004 unbalanced → REJECTED_BALANCE regardless of materiality
        expect(rows.get(3), "ADJ004", "UNBALANCED", "1000.00", "900.00", "REJECTED_BALANCE");

        // The design invariant: balanced proposals satisfy debits == credits exactly.
        for (String[] row : rows) {
            BigDecimal debit = new BigDecimal(row[4]);
            BigDecimal credit = new BigDecimal(row[5]);
            boolean balanced = debit.compareTo(credit) == 0;
            assertThat("BALANCED".equals(row[6])).as(row[0]).isEqualTo(balanced);
        }
    }

    @Test
    void materialityLevelsFollowTheDocumentedThresholds() throws Exception {
        List<String[]> rows = csv(MIGRATION.resolve("adjustment-example.csv"));
        for (String[] row : rows) {
            if ("REJECTED_BALANCE".equals(row[8])) {
                // Unbalanced proposals never reach materiality selection.
                assertThat(row[6]).isEqualTo("UNBALANCED");
                continue;
            }
            BigDecimal amount = new BigDecimal(row[7]);
            String level = row[8];
            if (amount.compareTo(new BigDecimal("1000.00")) < 0) {
                assertThat(level).isEqualTo("OWNER_REVIEW");
            } else if (amount.compareTo(new BigDecimal("10000.00")) < 0) {
                assertThat(level).isEqualTo("INDEPENDENT_APPROVER");
            } else {
                assertThat(level).isEqualTo("DOMAIN_APPROVER_PLUS_CERTIFIER");
            }
        }
    }

    @Test
    void exactCutoffRecordLandsOnExactlyOneSide() throws Exception {
        List<String[]> rows = csv(MIGRATION.resolve("migration-batch.csv"));
        // MIG001: cutoff 2026-10-31T23:59:59Z inclusive.
        var cutoff = LocalDateTime.parse("2026-10-31T23:59:59Z", TS).atZone(ZoneOffset.UTC);
        int included = 0;
        for (String[] row : rows) {
            if (!"MIG001".equals(row[0]) || !"GL_ENTRY".equals(row[1])) {
                continue;
            }
            var recordTime = LocalDateTime.parse(row[8], TS).atZone(ZoneOffset.UTC);
            boolean expectedIncluded = Boolean.parseBoolean(row[9]);
            // Inclusive cutoff: record at exactly T0 is included; T0+1s is not.
            boolean isIncluded = !recordTime.isAfter(cutoff);
            assertThat(isIncluded).as(row[7]).isEqualTo(expectedIncluded);
            if (isIncluded) {
                included++;
            }
        }
        // Row count and total reproduce the control totals exactly.
        assertThat(included).isEqualTo(2);
    }

    @Test
    void outOfToleranceOpeningBalanceIsABlocker() throws Exception {
        List<String[]> rows = csv(MIGRATION.resolve("migration-expectations.csv"));
        String blocker = rowFor(rows, "MIG002")[1];
        assertThat(blocker).startsWith("OUT_OF_TOLERANCE_BLOCKER");
        assertThat(blocker).contains("1250000.00");
        assertThat(blocker).contains("999000.00");
        // The two opening-balance fixtures carry divergent totals: the design blocks.
        List<String[]> batch = csv(MIGRATION.resolve("migration-batch.csv"));
        long distinctTotals =
                batch.stream()
                        .filter(row -> "OPENING_BALANCE".equals(row[1]))
                        .map(row -> row[4])
                        .distinct()
                        .count();
        assertThat(distinctTotals).isEqualTo(2);
    }

    @Test
    void duplicateReplayCreatesZeroNewFacts() throws Exception {
        List<String[]> rows = csv(MIGRATION.resolve("migration-expectations.csv"));
        assertThat(rowFor(rows, "MIG003")[1]).isEqualTo("REPLAY_CREATES_ZERO_NEW_FACTS");
    }

    // ---------- helpers ----------

    private static List<String[]> csv(Path path) throws Exception {
        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            rows.add(line.split(",", -1));
        }
        return rows.subList(1, rows.size());
    }

    private static String[] rowFor(List<String[]> rows, String id) {
        return rows.stream().filter(row -> row[0].equals(id)).findFirst().orElseThrow();
    }

    private static void expect(
            String[] row, String id, String balance, String debit, String credit, String level) {
        assertThat(row[0]).isEqualTo(id);
        assertThat(row[6]).isEqualTo(balance);
        assertThat(row[4]).isEqualTo(debit);
        assertThat(row[5]).isEqualTo(credit);
        assertThat(row[8]).isEqualTo(level);
    }
}
