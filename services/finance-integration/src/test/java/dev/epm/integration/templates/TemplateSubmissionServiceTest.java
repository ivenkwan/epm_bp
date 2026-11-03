package dev.epm.integration.templates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Task 18 template intake: valid submission accepted and landed; invalid sign/unknown column
 * produce coordinate-specific errors; exact-size boundary accepted and size+1 rejected before
 * parsing; corrected resubmission becomes a new revision with the original preserved.
 */
@QuarkusTest
class TemplateSubmissionServiceTest {

    @Inject TemplateSubmissionService templates;

    private static final String HEADER = "accountId,effectiveDate,currency,amount";

    @Test
    void validSubmissionIsAcceptedAndLanded() {
        TemplateSubmissionEntity submission =
                templates.submit(
                        "ENT101",
                        "treasury-daily-v3",
                        "2026-11",
                        "tmpl-v1",
                        HEADER + "\nACC001,2026-11-01,USD,100.25\nACC001,2026-11-01,USD,-40.00",
                        "preparer@example.invalid");
        assertThat(submission.getStatus()).isEqualTo("ACCEPTED");
        assertThat(submission.getRevision()).isEqualTo(1);
        assertThat(submission.getLoadId()).isNotNull();
        assertThat(RowErrorEntity.countFor(submission.getSubmissionId())).isZero();
    }

    @Test
    void invalidAmountAndCurrencyProduceCoordinateSpecificErrors() {
        TemplateSubmissionEntity submission =
                templates.submit(
                        "ENT101",
                        "c",
                        "2026-11",
                        "tmpl-v1",
                        HEADER + "\nACC001,2026-11-01,usd,abc",
                        "preparer@example.invalid");
        assertThat(submission.getStatus()).isEqualTo("REJECTED");
        assertThat(submission.getLoadId()).isNull();
        // Coordinate-specific: row 1, the exact offending columns.
        assertThat(RowErrorEntity.countFor(submission.getSubmissionId())).isEqualTo(2);
    }

    @Test
    void unrecognizedColumnIsRejectedBeforeAnyRowProcessing() {
        assertThatThrownBy(
                        () ->
                                templates.submit(
                                        "ENT101",
                                        "c",
                                        "2026-11",
                                        "tmpl-v1",
                                        "account,when,cur,amt\nACC001,2026-11-01,USD,1.00",
                                        "preparer@example.invalid"))
                .isInstanceOf(TemplateSubmissionService.TemplateException.class)
                .hasMessageContaining("unrecognized columns");
    }

    @Test
    void exactSizeBoundaryIsAcceptedAndSizePlusOneIsRejectedBeforeParsing() {
        // Exactly at the boundary: pad the (format-free) accountId column so the
        // payload measures exactly the configured maximum.
        String tail = ",2026-11-01,USD,1.00";
        int fixed = (HEADER + "\n" + tail).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int pad = TemplateSubmissionService.MAX_UPLOAD_BYTES - fixed;
        String exactPayload = HEADER + "\n" + "A".repeat(pad) + tail;
        assertThat(exactPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isEqualTo(TemplateSubmissionService.MAX_UPLOAD_BYTES);

        TemplateSubmissionEntity atBoundary =
                templates.submit(
                        "ENT101",
                        "c",
                        "2026-11",
                        "tmpl-v1",
                        exactPayload,
                        "preparer@example.invalid");
        assertThat(atBoundary.getStatus()).isIn("ACCEPTED", "REJECTED");

        String over = exactPayload + "A";
        assertThatThrownBy(
                        () ->
                                templates.submit(
                                        "ENT101",
                                        "c",
                                        "2026-11",
                                        "tmpl-v1",
                                        over,
                                        "preparer@example.invalid"))
                .hasMessageContaining("maximum size");
    }

    @Test
    void correctedResubmissionIsANewRevisionWithTheOriginalPreserved() {
        String period = "2026-11";
        TemplateSubmissionEntity original =
                templates.submit(
                        "ENT102",
                        "treasury-daily-v3",
                        period,
                        "tmpl-v1",
                        HEADER + "\nACC002,2026-11-01,EUR,xyz",
                        "preparer@example.invalid");
        assertThat(original.getStatus()).isEqualTo("REJECTED");
        assertThat(original.getRevision()).isEqualTo(1);

        TemplateSubmissionEntity corrected =
                templates.submit(
                        "ENT102",
                        "treasury-daily-v3",
                        period,
                        "tmpl-v1",
                        HEADER + "\nACC002,2026-11-01,EUR,50.00",
                        "preparer@example.invalid");
        assertThat(corrected.getRevision()).isEqualTo(2);
        assertThat(corrected.getStatus()).isEqualTo("ACCEPTED");
        // The original rejection evidence is untouched.
        TemplateSubmissionEntity reloadedOriginal =
                TemplateSubmissionEntity.findBySubmissionId(original.getSubmissionId());
        assertThat(reloadedOriginal.getStatus()).isEqualTo("REJECTED");
        assertThat(RowErrorEntity.countFor(original.getSubmissionId())).isEqualTo(1);
    }
}
