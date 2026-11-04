package dev.epm.planning.fx;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 40 acceptance (AC-F-023-01–03): deterministic local→group translation with exactly one
 * applicable approved rate per currency/date/type; no inference from adjacent periods; no
 * substitution of unapproved rates; zero stays zero; null is a required-input error; precision
 * follows the declared rounding basis; adjacent FX versions stay isolated; the governed DMN
 * decision table agrees with the service.
 */
class FxTranslationTest {

    private static final LocalDate RATE_DATE = LocalDate.of(2026, 1, 31);
    private static final FxTranslationService.RoundingBasis BASIS =
            new FxTranslationService.RoundingBasis(2, RoundingMode.HALF_UP);

    private final FxTranslationService service = new FxTranslationService();

    private static FxTranslationService.FxRate approvedRate(
            String version, LocalDate date, String rate) {
        return new FxTranslationService.FxRate(
                version,
                "EUR",
                "USD",
                date,
                new BigDecimal(rate),
                FxTranslationService.FxVersionStatus.APPROVED);
    }

    private FxTranslationService.TranslationRequest request(String amount) {
        return new FxTranslationService.TranslationRequest(
                "fx-2026-01",
                "EUR",
                "USD",
                RATE_DATE,
                amount == null ? null : new BigDecimal(amount));
    }

    @Test
    void localToGroupTranslationUsesExactlyOneApprovedRate() {
        FxTranslationService.TranslationResult result =
                service.translate(
                        request("1000.00"),
                        List.of(approvedRate("fx-2026-01", RATE_DATE, "1.0850000000")),
                        BASIS);
        assertThat(result.status()).isEqualTo(FxTranslationService.TranslationStatus.TRANSLATED);
        assertThat(result.groupAmount()).isEqualByComparingTo("1085.00");
        assertThat(result.rateApplied()).isEqualByComparingTo("1.085");
        assertThat(result.fxVersionId()).isEqualTo("fx-2026-01");
    }

    @Test
    void missingEffectiveRateNeverInfersFromAdjacentPeriods() {
        // Rate exists for Jan 31 and Mar 31, but not Feb 28 — Feb must fail closed.
        FxTranslationService.TranslationResult result =
                service.translate(
                        new FxTranslationService.TranslationRequest(
                                "fx-2026-01",
                                "EUR",
                                "USD",
                                LocalDate.of(2026, 2, 28),
                                new BigDecimal("500.00")),
                        List.of(
                                approvedRate("fx-2026-01", RATE_DATE, "1.085"),
                                approvedRate("fx-2026-01", LocalDate.of(2026, 3, 31), "1.09")),
                        BASIS);
        assertThat(result.status()).isEqualTo(FxTranslationService.TranslationStatus.MISSING_RATE);
        assertThat(result.groupAmount()).isNull();
        assertThat(result.qualityNote()).contains("never inferred from adjacent periods");
    }

    @Test
    void adjacentFxVersionsNeverSubstituteForEachOther() {
        // v1 has the Jan 31 rate; the caller pinned v2, which has none — no fallback.
        FxTranslationService.TranslationResult result =
                service.translate(
                        new FxTranslationService.TranslationRequest(
                                "fx-2026-02", "EUR", "USD", RATE_DATE, new BigDecimal("500.00")),
                        List.of(approvedRate("fx-2026-01", RATE_DATE, "1.085")),
                        BASIS);
        assertThat(result.status()).isEqualTo(FxTranslationService.TranslationStatus.MISSING_RATE);
        assertThat(result.qualityNote()).contains("never inferred");
    }

    @Test
    void unapprovedRatesAreNeverSubstituted() {
        FxTranslationService.FxRate draftRate =
                new FxTranslationService.FxRate(
                        "fx-draft",
                        "EUR",
                        "USD",
                        RATE_DATE,
                        new BigDecimal("1.10"),
                        FxTranslationService.FxVersionStatus.DRAFT);
        // Caller pinned the draft version directly.
        FxTranslationService.TranslationResult pinned =
                service.translate(
                        new FxTranslationService.TranslationRequest(
                                "fx-draft", "EUR", "USD", RATE_DATE, new BigDecimal("100.00")),
                        List.of(draftRate),
                        BASIS);
        assertThat(pinned.status())
                .isEqualTo(FxTranslationService.TranslationStatus.UNAPPROVED_RATE);
        assertThat(pinned.qualityNote()).contains("DRAFT").contains("never substituted");

        // Caller pinned an approved version with no rate; the draft rate is not borrowed.
        FxTranslationService.TranslationResult notBorrowed =
                service.translate(
                        request("100.00"),
                        List.of(
                                approvedRate("fx-2026-01", LocalDate.of(2026, 3, 31), "1.09"),
                                draftRate),
                        BASIS);
        assertThat(notBorrowed.status())
                .isEqualTo(FxTranslationService.TranslationStatus.MISSING_RATE);
        assertThat(notBorrowed.qualityNote()).contains("unapproved").contains("never substituted");
    }

    @Test
    void zeroLocalValueTranslatesToExactlyZero() {
        FxTranslationService.TranslationResult result =
                service.translate(
                        request("0"),
                        List.of(approvedRate("fx-2026-01", RATE_DATE, "7.2500000000")),
                        BASIS);
        assertThat(result.status()).isEqualTo(FxTranslationService.TranslationStatus.TRANSLATED);
        assertThat(result.groupAmount()).isEqualByComparingTo("0.00");
        assertThat(result.groupAmount().scale()).isEqualTo(2);
        assertThat(result.groupAmount().signum()).isZero();
    }

    @Test
    void nullLocalValueIsARequiredInputErrorAndIsNeverCoercedToZero() {
        FxTranslationService.TranslationResult result =
                service.translate(
                        request(null),
                        List.of(approvedRate("fx-2026-01", RATE_DATE, "1.085")),
                        BASIS);
        assertThat(result.status()).isEqualTo(FxTranslationService.TranslationStatus.NULL_INPUT);
        assertThat(result.groupAmount()).isNull();
        assertThat(result.qualityNote()).contains("never coerced to zero");
    }

    @Test
    void precisionBoundariesFollowTheDeclaredRoundingBasis() {
        // 10.00 * 1.085 = 10.85 exactly; 10.01 * 1.085 = 10.86085 → HALF_UP scale 2 → 10.86.
        assertThat(
                        service.translate(
                                        request("10.00"),
                                        List.of(approvedRate("fx-2026-01", RATE_DATE, "1.085")),
                                        BASIS)
                                .groupAmount())
                .isEqualByComparingTo("10.85");
        assertThat(
                        service.translate(
                                        request("10.01"),
                                        List.of(approvedRate("fx-2026-01", RATE_DATE, "1.085")),
                                        BASIS)
                                .groupAmount())
                .isEqualByComparingTo("10.86");

        // Half-way case at scale 2: 0.125 → HALF_UP 0.13, HALF_EVEN 0.12.
        assertThat(
                        service.translate(
                                        request("0.125"),
                                        List.of(approvedRate("fx-2026-01", RATE_DATE, "1")),
                                        BASIS)
                                .groupAmount())
                .isEqualByComparingTo("0.13");
        assertThat(
                        service.translate(
                                        request("0.125"),
                                        List.of(approvedRate("fx-2026-01", RATE_DATE, "1")),
                                        new FxTranslationService.RoundingBasis(
                                                2, RoundingMode.HALF_EVEN))
                                .groupAmount())
                .isEqualByComparingTo("0.12");
    }

    @Test
    void declaredRoundingReconciliationReportsTheLineVersusTotalDifference() {
        // 3 × 10.005 → each 10.01 (HALF_UP), sum 30.03; total 30.015 → 30.02; diff 0.01 declared.
        FxTranslationService.RoundingReconciliation reconciliation =
                service.reconcile(
                        List.of(request("10.005"), request("10.005"), request("10.005")),
                        List.of(approvedRate("fx-2026-01", RATE_DATE, "1")),
                        BASIS);
        assertThat(reconciliation.sumOfTranslatedLines()).isEqualByComparingTo("30.03");
        assertThat(reconciliation.translatedTotal()).isEqualByComparingTo("30.02");
        assertThat(reconciliation.roundingDifference()).isEqualByComparingTo("0.01");
    }

    @Test
    void reconciliationRefusesLinesThatAreNotTranslated() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.reconcile(List.of(request("1.00")), List.of(), BASIS));
    }

    @Test
    void duplicateOrNonPositiveRatesAreIntegrityViolations() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () ->
                        service.translate(
                                request("1.00"),
                                List.of(
                                        approvedRate("fx-2026-01", RATE_DATE, "1.085"),
                                        approvedRate("fx-2026-01", RATE_DATE, "1.086")),
                                BASIS));

        FxTranslationService.FxRate zeroRate =
                new FxTranslationService.FxRate(
                        "fx-2026-01",
                        "EUR",
                        "USD",
                        RATE_DATE,
                        BigDecimal.ZERO,
                        FxTranslationService.FxVersionStatus.APPROVED);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.translate(request("1.00"), List.of(zeroRate), BASIS));
    }

    @Test
    void governedDmnDecisionTableAgreesWithTheServicePolicy() throws Exception {
        Path dmn = dmnPath();
        var helper = new org.kie.internal.utils.KieHelper();
        helper.addContent(Files.readString(dmn), org.kie.api.io.ResourceType.DMN);
        var kbase = helper.build();
        var runtime =
                org.kie.api.runtime.KieRuntimeFactory.of(kbase)
                        .get(org.kie.dmn.api.core.DMNRuntime.class);
        var model = runtime.getModel("https://epm.dev/rules/planning", "fx-translation");
        assertThat(model).isNotNull();

        assertThat(decide(runtime, model, false, true, true)).isEqualTo("NULL_INPUT");
        assertThat(decide(runtime, model, true, false, true)).isEqualTo("MISSING_RATE");
        assertThat(decide(runtime, model, true, true, false)).isEqualTo("UNAPPROVED_RATE");
        assertThat(decide(runtime, model, true, true, true)).isEqualTo("TRANSLATED");
        // Service outcomes line up with the governed table for the same facts.
        assertThat(
                        service.translate(
                                        request("1.00"),
                                        List.of(approvedRate("fx-2026-01", RATE_DATE, "1.085")),
                                        BASIS)
                                .status()
                                .name())
                .isEqualTo(decide(runtime, model, true, true, true));
        assertThat(service.translate(request("1.00"), List.of(), BASIS).status().name())
                .isEqualTo(decide(runtime, model, true, false, false));
    }

    private static String decide(
            org.kie.dmn.api.core.DMNRuntime runtime,
            org.kie.dmn.api.core.DMNModel model,
            boolean localPresent,
            boolean rateFound,
            boolean rateApproved) {
        var context = runtime.newContext();
        context.set("localValuePresent", localPresent);
        context.set("rateFound", rateFound);
        context.set("rateVersionApproved", rateApproved);
        return (String)
                runtime.evaluateAll(model, context)
                        .getDecisionResultByName("FX Translation Status")
                        .getResult();
    }

    private static Path dmnPath() {
        List<Path> candidates =
                List.of(
                        Path.of("rules", "dmn", "planning", "fx-translation.dmn"),
                        Path.of("..", "..", "rules", "dmn", "planning", "fx-translation.dmn"));
        return candidates.stream().filter(Files::exists).findFirst().orElseThrow();
    }
}
