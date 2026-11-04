package dev.epm.reporting.exportformats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 45 governed export acceptance (AC-F-038/F-041): Excel/PDF/presentation exports carry
 * watermark, edition-content-hash equivalence, retention classification, recipient
 * re-authorization, a delivery log, explicit row-limit truncation, and action tracking; refresh
 * never publishes automatically.
 */
class ExportFormatServiceTest {

    private final ExportFormatService service = new ExportFormatService();

    private static ExportFormatService.ExportRequest request(
            String watermark, String retention, List<String> recipients) {
        return new ExportFormatService.ExportRequest(
                ExportFormatService.Format.PDF,
                "ed-9",
                "a".repeat(64),
                5000,
                4200,
                watermark,
                retention,
                recipients,
                List.of("cfo@example.invalid", "controller@example.invalid"));
    }

    @Test
    void happyPathDeliversToReAuthorizedRecipientsWithEquivalenceStamp() {
        ExportFormatService.ExportResult result =
                service.export(
                        request(
                                "INTERNAL — governed export",
                                "RETENTION_7Y",
                                List.of("cfo@example.invalid", "guest@example.invalid")));
        assertThat(result.exported()).isTrue();
        assertThat(result.artifact().contentHash()).hasSize(64);
        assertThat(result.artifact().watermark()).contains("governed export");
        assertThat(result.deliveryLog())
                .anySatisfy(
                        d -> {
                            assertThat(d.recipient()).isEqualTo("cfo@example.invalid");
                            assertThat(d.delivered()).isTrue();
                        })
                .anySatisfy(
                        d -> {
                            assertThat(d.recipient()).isEqualTo("guest@example.invalid");
                            assertThat(d.delivered()).isFalse();
                            assertThat(d.reason()).contains("not re-authorized");
                        });
    }

    @Test
    void watermarkRetentionAndEditionHashAreMandatory() {
        assertThat(service.export(request("  ", "RETENTION_7Y", List.of())).note())
                .contains("watermark is mandatory");
        assertThat(service.export(request("WM", "  ", List.of())).note())
                .contains("retention classification is mandatory");
        ExportFormatService.ExportRequest noHash =
                new ExportFormatService.ExportRequest(
                        ExportFormatService.Format.EXCEL,
                        "ed-9",
                        null,
                        100,
                        10,
                        "WM",
                        "RETENTION_7Y",
                        List.of("cfo@example.invalid"),
                        List.of("cfo@example.invalid"));
        assertThat(service.export(noHash).note()).contains("edition content hash");
    }

    @Test
    void rowLimitsTruncateWithAnExplicitNoticeNeverSilently() {
        ExportFormatService.ExportRequest over =
                new ExportFormatService.ExportRequest(
                        ExportFormatService.Format.EXCEL,
                        "ed-9",
                        "b".repeat(64),
                        1000,
                        1500,
                        "WM",
                        "RETENTION_7Y",
                        List.of("cfo@example.invalid"),
                        List.of("cfo@example.invalid"));
        ExportFormatService.ExportResult result = service.export(over);
        assertThat(result.artifact().truncated()).isTrue();
        assertThat(result.artifact().deliveredRows()).isEqualTo(1000);
        assertThat(result.artifact().truncationNotice())
                .contains("1000")
                .contains("1500")
                .contains("no rows beyond the limit");

        ExportFormatService.ExportRequest within =
                new ExportFormatService.ExportRequest(
                        ExportFormatService.Format.PRESENTATION,
                        "ed-9",
                        "c".repeat(64),
                        1000,
                        900,
                        "WM",
                        "RETENTION_7Y",
                        List.of("cfo@example.invalid"),
                        List.of("cfo@example.invalid"));
        assertThat(service.export(within).artifact().truncated()).isFalse();
        assertThat(service.export(within).artifact().truncationNotice()).isNull();
    }

    @Test
    void unauthorizedEverywhereDeliversNothing() {
        ExportFormatService.ExportResult none =
                service.export(request("WM", "RETENTION_7Y", List.of("guest@example.invalid")));
        assertThat(none.exported()).isFalse();
        assertThat(none.note()).contains("no recipient was re-authorized");
    }

    @Test
    void refreshProducesANewEditionForReviewAndNeverPublishesAutomatically() {
        ExportFormatService.RefreshOutcome outcome = service.refresh("ed-9", "ed-10");
        assertThat(outcome.newEditionId()).isEqualTo("ed-10");
        assertThat(outcome.published())
                .as("publication and supersession are named-human acts")
                .isFalse();
    }
}
