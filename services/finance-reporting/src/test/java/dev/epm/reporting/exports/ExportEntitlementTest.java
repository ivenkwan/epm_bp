package dev.epm.reporting.exports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 31 export entitlement: certified-source equivalence (content carries the edition and
 * watermark), unauthorized recipient denied at distribution time, configured row-limit overflow
 * refused with no silent truncation, delivery evidence with retention class and correlation, and
 * action completion with independent verification (timers never verify or close).
 */
class ExportEntitlementTest {

    private final ExportService exports = new ExportService();

    @Test
    void exportCarriesEditionWatermarkAndCorrelationMetadata() {
        ExportService.ExportResult result =
                exports.export(
                        new ExportService.ExportRequest(
                                "ed-2026-11-01",
                                "CSV",
                                "board@example.invalid",
                                List.of(
                                        new String[] {"entity", "closing"},
                                        new String[] {"GROUP", "1941.00"}),
                                100,
                                "corr-exp-1"));
        assertThat(result.refusedBecause()).isNull();
        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(result.content()).startsWith("# edition=ed-2026-11-01 watermark=certified");
        assertThat(result.content()).contains("correlation=corr-exp-1");
        // Certified-source equivalence: the figures render exactly as stored (decimal strings).
        assertThat(result.content()).contains("1941.00");
    }

    @Test
    void unauthorizedRecipientIsDeniedAtDistributionTime() {
        ExportService.Distribution denied = exports.distribute(false, "CONFIDENTIAL");
        assertThat(denied.delivered()).isFalse();
        assertThat(denied.reason()).contains("not entitled");
        assertThat(denied.retentionClass()).isNull();

        ExportService.Distribution delivered = exports.distribute(true, "CONFIDENTIAL");
        assertThat(delivered.delivered()).isTrue();
        assertThat(delivered.retentionClass()).isEqualTo("CONFIDENTIAL");
    }

    @Test
    void rowLimitOverflowRefusesWithNoSilentTruncation() {
        ExportService.ExportResult overflow =
                exports.export(
                        new ExportService.ExportRequest(
                                "ed-1",
                                "CSV",
                                "r@e",
                                List.of(new String[] {"a"}, new String[] {"b"}, new String[] {"c"}),
                                2,
                                null));
        assertThat(overflow.refusedBecause()).contains("exceeds the configured limit 2");
        assertThat(overflow.content()).as("no partial file is produced").isNull();
    }

    @Test
    void actionCompletionRequiresEvidenceAndIndependentVerification() {
        ExportService.ActionItem open =
                new ExportService.ActionItem(
                        "act-1",
                        "Resolve reconciliation break",
                        "treasury@example.invalid",
                        OffsetDateTime.of(2026, 11, 4, 12, 0, 0, 0, ZoneOffset.UTC),
                        null,
                        null,
                        null,
                        null);
        // No evidence: verification cannot pass.
        assertThat(exports.verify(open, "verifier@e")).isFalse();

        ExportService.ActionItem decided =
                exports.decide(open, "RESOLVED", "owner@e", "case-42 evidence bundle");
        assertThat(decided.completionEvidence()).isEqualTo("case-42 evidence bundle");

        // Self-verification denied; independent verifier passes.
        assertThat(exports.verify(decided, "owner@e")).isFalse();
        assertThat(exports.verify(decided, "verifier@e")).isTrue();
    }
}
