package dev.epm.planning.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Task 40 governed FX translation: converting a local-currency value into the group currency uses
 * exactly one applicable approved rate per currency pair, effective date, and rate type. Rates are
 * never inferred from adjacent periods, never substituted from unapproved FX versions, and never
 * fallback-translated. Zero local value translates to exactly zero; a null value is a
 * required-input error and is never coerced to zero. Rounding follows the version's declared mode
 * and scale, and the per-line versus translated-total difference is reported as the declared
 * rounding reconciliation.
 */
public class FxTranslationService {

    /** Approval state of an FX version (mirrors {@code epm_fx_version.status}). */
    public enum FxVersionStatus {
        DRAFT,
        APPROVED,
        RETIRED
    }

    /** Translation quality status; only TRANSLATED carries a value. */
    public enum TranslationStatus {
        TRANSLATED,
        MISSING_RATE,
        UNAPPROVED_RATE,
        NULL_INPUT
    }

    /** One governed rate row (from {@code epm_fx_rate}) with its version's status. */
    public record FxRate(
            String fxVersionId,
            String fromCurrency,
            String toCurrency,
            LocalDate effectiveDate,
            BigDecimal rate,
            FxVersionStatus versionStatus) {}

    /** Declared rounding basis of an approved FX version. */
    public record RoundingBasis(int outputScale, RoundingMode mode) {}

    /** Translation request: exact effective date, pinned FX version, local amount. */
    public record TranslationRequest(
            String fxVersionId,
            String fromCurrency,
            String toCurrency,
            LocalDate effectiveDate,
            BigDecimal localAmount) {}

    /** Translation outcome with visible governance labels. */
    public record TranslationResult(
            TranslationStatus status,
            BigDecimal groupAmount,
            String fxVersionId,
            BigDecimal rateApplied,
            RoundingBasis roundingBasis,
            String qualityNote) {

        static TranslationResult failed(
                TranslationStatus status, String qualityNote, String fxVersionId) {
            return new TranslationResult(status, null, fxVersionId, null, null, qualityNote);
        }
    }

    /**
     * Translates one local value to the group currency. The rate lookup is an exact match on
     * (fxVersionId, from, to, effectiveDate) within APPROVED versions only — no adjacent-period
     * inference, no cross-version substitution.
     */
    public TranslationResult translate(
            TranslationRequest request, List<FxRate> availableRates, RoundingBasis basis) {
        Objects.requireNonNull(basis, "rounding basis");
        if (request.localAmount() == null) {
            return TranslationResult.failed(
                    TranslationStatus.NULL_INPUT,
                    "null local value is a required input and is never coerced to zero",
                    request.fxVersionId());
        }
        List<FxRate> candidates =
                availableRates.stream()
                        .filter(
                                r ->
                                        r.fxVersionId().equals(request.fxVersionId())
                                                && r.fromCurrency().equals(request.fromCurrency())
                                                && r.toCurrency().equals(request.toCurrency())
                                                && r.effectiveDate()
                                                        .equals(request.effectiveDate()))
                        .toList();
        if (candidates.isEmpty()) {
            // An unapproved version may hold the rate, but it is never substituted.
            boolean unapprovedExists =
                    availableRates.stream()
                            .anyMatch(
                                    r ->
                                            r.fromCurrency().equals(request.fromCurrency())
                                                    && r.toCurrency().equals(request.toCurrency())
                                                    && r.effectiveDate()
                                                            .equals(request.effectiveDate())
                                                    && r.versionStatus()
                                                            != FxVersionStatus.APPROVED);
            return TranslationResult.failed(
                    TranslationStatus.MISSING_RATE,
                    unapprovedExistenceNote(unapprovedExists, request),
                    request.fxVersionId());
        }
        FxRate rate = candidates.get(0);
        if (rate.versionStatus() != FxVersionStatus.APPROVED) {
            return TranslationResult.failed(
                    TranslationStatus.UNAPPROVED_RATE,
                    "rate exists only under a "
                            + rate.versionStatus()
                            + " FX version; unapproved rates are never substituted",
                    request.fxVersionId());
        }
        if (candidates.size() > 1) {
            throw new IllegalStateException(
                    "more than one rate for the same version/currency/date key — data integrity"
                            + " violation");
        }
        if (rate.rate().signum() <= 0) {
            throw new IllegalStateException("non-positive rate — data integrity violation");
        }
        // Zero stays exactly zero at the declared scale; no rate multiplication needed.
        BigDecimal groupAmount;
        if (request.localAmount().signum() == 0) {
            groupAmount = request.localAmount().setScale(basis.outputScale(), basis.mode());
        } else {
            groupAmount =
                    request.localAmount()
                            .multiply(rate.rate())
                            .setScale(basis.outputScale(), basis.mode());
        }
        return new TranslationResult(
                TranslationStatus.TRANSLATED,
                groupAmount,
                rate.fxVersionId(),
                rate.rate(),
                basis,
                null);
    }

    /**
     * Declared rounding reconciliation: per-line translation (each line rounded at the declared
     * basis) versus translating the summed local amounts. The difference is reported, never
     * silently absorbed.
     */
    public record RoundingReconciliation(
            BigDecimal sumOfTranslatedLines,
            BigDecimal translatedTotal,
            BigDecimal roundingDifference) {}

    /** Computes the declared rounding reconciliation across translated lines. */
    public RoundingReconciliation reconcile(
            List<TranslationRequest> lines, List<FxRate> availableRates, RoundingBasis basis) {
        BigDecimal sumOfLines = BigDecimal.ZERO;
        BigDecimal localSum = BigDecimal.ZERO;
        for (TranslationRequest line : lines) {
            TranslationResult result = translate(line, availableRates, basis);
            if (result.status() != TranslationStatus.TRANSLATED) {
                throw new IllegalArgumentException(
                        "cannot reconcile a line with status " + result.status());
            }
            sumOfLines = sumOfLines.add(result.groupAmount());
            localSum = localSum.add(line.localAmount());
        }
        TranslationResult total =
                translate(
                        new TranslationRequest(
                                lines.get(0).fxVersionId(),
                                lines.get(0).fromCurrency(),
                                lines.get(0).toCurrency(),
                                lines.get(0).effectiveDate(),
                                localSum),
                        availableRates,
                        basis);
        return new RoundingReconciliation(
                sumOfLines, total.groupAmount(), sumOfLines.subtract(total.groupAmount()));
    }

    private static String unapprovedExistenceNote(boolean unapprovedExists, TranslationRequest r) {
        if (unapprovedExists) {
            return "no approved rate for the exact date; a rate exists only under an unapproved"
                    + " version and is never substituted";
        }
        return "no rate for the exact effective date "
                + r.effectiveDate()
                + "; rates are never inferred from adjacent periods";
    }
}
