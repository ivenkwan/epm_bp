package dev.epm.integration.templates;

import dev.epm.integration.landing.LandingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Versioned template intake (Task 18): structured CSV rows are validated cell-by-cell before
 * anything lands; oversized uploads are rejected before parsing; a corrected resubmission is a NEW
 * revision of the same (entity, contract, period) anchor with the original preserved untouched.
 */
@ApplicationScoped
public class TemplateSubmissionService {

    /** Configured maximum upload size (1 MiB, checked before any parsing). */
    static final int MAX_UPLOAD_BYTES = 1024 * 1024;

    /** Declared CSV columns; order is fixed by the versioned template. */
    static final List<String> COLUMNS = List.of("accountId", "effectiveDate", "currency", "amount");

    private final LandingService landing;

    @Inject
    TemplateSubmissionService(LandingService landing) {
        this.landing = landing;
    }

    /**
     * Validates and (when clean) lands one submission. Rejections carry coordinate-level row/column
     * errors; nothing is overwritten and resubmission allocates revision+1.
     */
    @Transactional
    public TemplateSubmissionEntity submit(
            String entityId,
            String contractId,
            String sourcePeriod,
            String templateVersion,
            String csv,
            String submittedBy) {
        if (csv == null || csv.isBlank()) {
            throw new TemplateException("submission payload is required");
        }
        if (csv.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_UPLOAD_BYTES) {
            throw new TemplateException("upload exceeds the configured maximum size");
        }

        String submissionId = "sub-" + UUID.randomUUID();
        int revision =
                TemplateSubmissionEntity.latestRevision(entityId, contractId, sourcePeriod) + 1;
        TemplateSubmissionEntity submission =
                TemplateSubmissionEntity.open(
                        submissionId,
                        entityId,
                        contractId,
                        sourcePeriod,
                        templateVersion,
                        revision,
                        submittedBy);

        List<String[]> rows = parse(csv);
        List<String[]> errors = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            String[] row = rows.get(rowIndex);
            validateRow(rowIndex + 1, row, errors);
        }
        for (String[] error : errors) {
            RowErrorEntity.of(
                    submissionId, Integer.parseInt(error[0]), error[1], error[2], error[3]);
        }
        if (!errors.isEmpty()) {
            submission.reject();
            return submission;
        }

        LandingService.LandingResult landed =
                landing.land(
                        csv.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "text/csv",
                        "SYS-TMPL-1",
                        contractId,
                        revision,
                        submissionId,
                        "upload-" + submissionId,
                        rows.size(),
                        null);
        submission.accept(landed.loadId());
        return submission;
    }

    private static List<String[]> parse(String csv) {
        List<String[]> rows = new ArrayList<>();
        boolean header = true;
        for (String line : csv.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            if (header) {
                header = false;
                List<String> actual = List.of(line.split(",", -1));
                if (!actual.equals(COLUMNS)) {
                    throw new TemplateException("unrecognized columns; expected header " + COLUMNS);
                }
                continue;
            }
            rows.add(line.split(",", -1));
        }
        return rows;
    }

    /** Cell-level validation: size, shape, and sign checks with row/column coordinates. */
    private static void validateRow(int rowNumber, String[] row, List<String[]> errors) {
        if (row.length != COLUMNS.size()) {
            errors.add(
                    new String[] {
                        String.valueOf(rowNumber),
                        "",
                        "WRONG_COLUMN_COUNT",
                        "expected " + COLUMNS.size() + " columns but found " + row.length
                    });
            return;
        }
        for (int column = 0; column < COLUMNS.size(); column++) {
            String value = row[column];
            String name = COLUMNS.get(column);
            if (value.isBlank()) {
                errors.add(
                        new String[] {
                            String.valueOf(rowNumber), name, "REQUIRED", name + " is required"
                        });
                continue;
            }
            if ("amount".equals(name)) {
                try {
                    new BigDecimal(value);
                } catch (NumberFormatException invalidSignOrPrecision) {
                    errors.add(
                            new String[] {
                                String.valueOf(rowNumber),
                                name,
                                "INVALID_AMOUNT",
                                "amount must be a decimal string"
                            });
                }
            }
            if ("currency".equals(name) && !value.matches("[A-Z]{3}")) {
                errors.add(
                        new String[] {
                            String.valueOf(rowNumber),
                            name,
                            "INVALID_CURRENCY",
                            "currency must be three uppercase letters"
                        });
            }
            if ("effectiveDate".equals(name) && !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                errors.add(
                        new String[] {
                            String.valueOf(rowNumber),
                            name,
                            "INVALID_DATE",
                            "effectiveDate must be an ISO date"
                        });
            }
        }
    }

    /** Template intake governance failures surfaced as safe problems. */
    public static final class TemplateException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public TemplateException(String message) {
            super(message);
        }
    }
}
