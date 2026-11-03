package dev.epm.integration.migration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Initial historical migration control (Task 33, executing the signed Task 15 design). Batches are
 * resumable and idempotent (replay identity), control totals must match exactly, out-of-tolerance
 * opening balances are blockers, exact-cutoff records land on exactly one side, and a failed batch
 * leaves the fallback (legacy) path active.
 */
public final class MigrationRunner {

    private MigrationRunner() {}

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /** One legacy record as declared in the migration fixtures. */
    public record LegacyRecord(String recordId, String timestamp, String amount) {}

    /** One batch outcome. */
    public record BatchOutcome(
            String batchId, int rowsLoaded, String totalAmount, String outcome) {}

    /**
     * Loads one batch under the register's controls: cutoff inclusion, control totals, and replay
     * identity. Any failure is a governed refusal (the legacy path stays the source of truth).
     */
    public static BatchOutcome load(
            String batchId,
            String objectClass,
            List<LegacyRecord> records,
            String cutoffUtc,
            boolean cutoffInclusive,
            int expectedRows,
            String expectedTotal,
            List<String> alreadyLoadedIds) {
        LocalDateTime cutoff = LocalDateTime.parse(cutoffUtc, TS);
        BigDecimal total = BigDecimal.ZERO;
        int included = 0;
        for (LegacyRecord record : records) {
            if (alreadyLoadedIds.contains(record.recordId())) {
                // Replay identity: an already-loaded record creates no new fact.
                continue;
            }
            LocalDateTime at = LocalDateTime.parse(record.timestamp(), TS);
            boolean includedByCutoff = cutoffInclusive ? !at.isAfter(cutoff) : at.isBefore(cutoff);
            if (!includedByCutoff) {
                continue;
            }
            included++;
            total = total.add(new BigDecimal(record.amount()));
        }
        String totalString = total.setScale(2, RoundingMode.HALF_UP).toPlainString();

        // Control totals: row count and amount must match the register exactly.
        if (included != expectedRows || totalString.compareTo(expectedTotal) != 0) {
            return new BatchOutcome(batchId, included, totalString, "BLOCKED_TOLERANCE");
        }
        return new BatchOutcome(batchId, included, totalString, "LOADED");
    }

    /**
     * Opening-balance tolerance: the legacy and platform opening totals must agree exactly (0.01
     * tolerance); a breach is a blocker with both values shown.
     */
    public static String openingBalanceGate(String legacyTotal, String platformTotal) {
        BigDecimal difference =
                new BigDecimal(platformTotal)
                        .subtract(new BigDecimal(legacyTotal))
                        .abs()
                        .setScale(2, RoundingMode.HALF_UP);
        if (difference.compareTo(new BigDecimal("0.01")) > 0) {
            return "OUT_OF_TOLERANCE_BLOCKER: expected "
                    + legacyTotal
                    + " but platform holds "
                    + platformTotal
                    + " (difference "
                    + difference.toPlainString()
                    + ")";
        }
        return "OK";
    }

    /** Fallback stays active until an explicit GO decision is recorded. */
    public static boolean fallbackActive(String status) {
        return !"GO".equals(status);
    }

    @SuppressWarnings("unused")
    private static ZoneOffset unusedZoneAnchor() {
        return ZoneOffset.UTC;
    }
}
