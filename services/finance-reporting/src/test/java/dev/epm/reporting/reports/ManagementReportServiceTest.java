package dev.epm.reporting.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 39 acceptance: management reports and initial decision packs assemble from certified data
 * products and shared KPI figures only. Covers shared-figure equivalence, uncertified required
 * division, zero activity versus missing data, opening-balance reconciliation, retired KPI
 * references, empty required sections, immutable editions with approval history, and reproducible
 * regeneration.
 */
class ManagementReportServiceTest {

    private final ManagementReportService service = new ManagementReportService();

    private static final List<ManagementReportService.KpiReference> CERTIFIED_CATALOG =
            List.of(
                    new ManagementReportService.KpiReference(
                            "kpi-cash", ManagementReportService.KpiCatalogStatus.CERTIFIED),
                    new ManagementReportService.KpiReference(
                            "kpi-revenue", ManagementReportService.KpiCatalogStatus.CERTIFIED));

    private static final List<ManagementReportService.ScopeCertification> CERTIFIED_SCOPES =
            List.of(
                    new ManagementReportService.ScopeCertification("ENTERPRISE", true),
                    new ManagementReportService.ScopeCertification("DIVISION:north", true),
                    new ManagementReportService.ScopeCertification("ENTITY:north-1", true));

    private ManagementReportService.ReportDefinition divisionReport() {
        return new ManagementReportService.ReportDefinition(
                "rep-north-flash",
                ManagementReportService.Scope.DIVISION,
                "north",
                null,
                "North division flash",
                ManagementReportService.Layout.FLASH,
                List.of(
                        new ManagementReportService.SectionDefinition(
                                "cash", "Cash position", true, "cash_position", "PRIOR_PERIOD")),
                "fpa@example.invalid",
                1);
    }

    @Test
    void certificationSucceedsWithZeroActivityAsAGovernedValue() {
        ManagementReportService.CertificationResult result =
                service.certifyReport(
                        divisionReport(),
                        List.of(
                                new ManagementReportService.FigureInput(
                                        "cash",
                                        "fig-cash-north",
                                        "kpi-cash",
                                        new BigDecimal("0.00"),
                                        true)),
                        CERTIFIED_CATALOG,
                        CERTIFIED_SCOPES);
        assertThat(result.certified()).isTrue();
        assertThat(result.figures())
                .singleElement()
                .satisfies(
                        figure -> {
                            assertThat(figure.state())
                                    .isEqualTo(ManagementReportService.ActivityState.ZERO_ACTIVITY);
                            assertThat(figure.amount()).isEqualByComparingTo("0");
                        });
    }

    @Test
    void missingDataInARequiredSectionBlocksWhileOptionalSectionsMayFlag() {
        ManagementReportService.CertificationResult result =
                service.certifyReport(
                        divisionReport(),
                        List.of(
                                new ManagementReportService.FigureInput(
                                        "cash", "fig-cash-north", "kpi-cash", null, false)),
                        CERTIFIED_CATALOG,
                        CERTIFIED_SCOPES);
        assertThat(result.certified()).isFalse();
        assertThat(result.reason()).contains("missing data in required section").contains("cash");
    }

    @Test
    void emptyRequiredSectionBlocksCertification() {
        ManagementReportService.CertificationResult result =
                service.certifyReport(
                        divisionReport(), List.of(), CERTIFIED_CATALOG, CERTIFIED_SCOPES);
        assertThat(result.certified()).isFalse();
        assertThat(result.reason()).contains("empty required section").contains("cash");
    }

    @Test
    void uncertifiedRequiredDivisionScopeBlocksCertification() {
        ManagementReportService.ReportDefinition southReport =
                new ManagementReportService.ReportDefinition(
                        "rep-south-flash",
                        ManagementReportService.Scope.DIVISION,
                        "south",
                        null,
                        "South division flash",
                        ManagementReportService.Layout.FLASH,
                        divisionReport().sections(),
                        "fpa@example.invalid",
                        1);
        ManagementReportService.CertificationResult result =
                service.certifyReport(
                        southReport,
                        List.of(
                                new ManagementReportService.FigureInput(
                                        "cash",
                                        "fig-cash-south",
                                        "kpi-cash",
                                        new BigDecimal("10"),
                                        true)),
                        CERTIFIED_CATALOG,
                        CERTIFIED_SCOPES);
        assertThat(result.certified()).isFalse();
        assertThat(result.reason())
                .contains("required scope uncertified")
                .contains("DIVISION:south");
    }

    @Test
    void retiredKpiReferenceBlocksCertification() {
        List<ManagementReportService.KpiReference> catalogWithRetired =
                List.of(
                        new ManagementReportService.KpiReference(
                                "kpi-cash", ManagementReportService.KpiCatalogStatus.CERTIFIED),
                        new ManagementReportService.KpiReference(
                                "kpi-old-margin",
                                ManagementReportService.KpiCatalogStatus.RETIRED));
        ManagementReportService.CertificationResult result =
                service.certifyReport(
                        divisionReport(),
                        List.of(
                                new ManagementReportService.FigureInput(
                                        "cash",
                                        "fig-cash-north",
                                        "kpi-cash",
                                        new BigDecimal("5"),
                                        true),
                                new ManagementReportService.FigureInput(
                                        "cash",
                                        "fig-old-margin",
                                        "kpi-old-margin",
                                        new BigDecimal("1"),
                                        true)),
                        catalogWithRetired,
                        CERTIFIED_SCOPES);
        assertThat(result.certified()).isFalse();
        assertThat(result.reason())
                .contains("retired or unknown KPI reference")
                .contains("kpi-old-margin")
                .contains("RETIRED");
    }

    @Test
    void unknownKpiReferenceBlocksCertificationFailClosed() {
        ManagementReportService.CertificationResult result =
                service.certifyReport(
                        divisionReport(),
                        List.of(
                                new ManagementReportService.FigureInput(
                                        "cash",
                                        "fig-x",
                                        "kpi-never-seen",
                                        new BigDecimal("5"),
                                        true)),
                        CERTIFIED_CATALOG,
                        CERTIFIED_SCOPES);
        assertThat(result.certified()).isFalse();
        assertThat(result.reason()).contains("kpi-never-seen").contains("unknown");
    }

    @Test
    void invalidLayoutOrComparisonReferenceBlocks() {
        ManagementReportService.ReportDefinition badLayout =
                new ManagementReportService.ReportDefinition(
                        "rep-bad",
                        ManagementReportService.Scope.ENTERPRISE,
                        null,
                        null,
                        "Broken",
                        null,
                        divisionReport().sections(),
                        "fpa@example.invalid",
                        1);
        assertThat(
                        service.certifyReport(
                                        badLayout, List.of(), CERTIFIED_CATALOG, CERTIFIED_SCOPES)
                                .certified())
                .isFalse();

        ManagementReportService.ReportDefinition badComparison =
                new ManagementReportService.ReportDefinition(
                        "rep-bad-comp",
                        ManagementReportService.Scope.ENTERPRISE,
                        null,
                        null,
                        "Broken comparison",
                        ManagementReportService.Layout.FLASH,
                        List.of(
                                new ManagementReportService.SectionDefinition(
                                        "cash", "Cash", true, "cash_position", "LAST_YEAR_GUESS")),
                        "fpa@example.invalid",
                        1);
        ManagementReportService.CertificationResult blocked =
                service.certifyReport(
                        badComparison, List.of(), CERTIFIED_CATALOG, CERTIFIED_SCOPES);
        assertThat(blocked.certified()).isFalse();
        assertThat(blocked.reason()).contains("invalid comparison reference");
    }

    private ManagementReportService.MemberReport certifiedMember(
            String reportId, String kpiId, String amount) {
        return new ManagementReportService.MemberReport(
                reportId,
                true,
                true,
                List.of(
                        new ManagementReportService.CertifiedFigure(
                                "fig-" + reportId,
                                kpiId,
                                new BigDecimal(amount),
                                ManagementReportService.ActivityState.VALUE)));
    }

    private ManagementReportService.PackRequest boardPack(
            List<ManagementReportService.MemberReport> members, String opening, String prior) {
        return new ManagementReportService.PackRequest(
                "pack-board-q1",
                ManagementReportService.Layout.BOARD,
                "Board pack Q1",
                "cfo@example.invalid",
                members,
                opening == null ? null : new BigDecimal(opening),
                prior == null ? null : new BigDecimal(prior),
                Instant.parse("2026-03-31T18:00:00Z"),
                "generator@example.invalid");
    }

    @Test
    void sharedFiguresAcrossMemberReportsMustAgree() {
        ManagementReportService.PackGenerationResult diverged =
                service.generatePack(
                        boardPack(
                                List.of(
                                        certifiedMember("rep-a", "kpi-cash", "100.00"),
                                        certifiedMember("rep-b", "kpi-cash", "101.00")),
                                "1000.00",
                                "1000.00"));
        assertThat(diverged.generated()).isFalse();
        assertThat(diverged.reason()).contains("shared figure divergence").contains("kpi-cash");
    }

    @Test
    void boardPackRequiresOpeningBalanceReconciliation() {
        ManagementReportService.PackGenerationResult mismatch =
                service.generatePack(
                        boardPack(
                                List.of(certifiedMember("rep-a", "kpi-cash", "100.00")),
                                "1000.00",
                                "990.00"));
        assertThat(mismatch.generated()).isFalse();
        assertThat(mismatch.reason()).contains("does not reconcile");

        ManagementReportService.PackGenerationResult missing =
                service.generatePack(
                        boardPack(List.of(certifiedMember("rep-a", "kpi-cash", "1")), null, null));
        assertThat(missing.generated()).isFalse();
        assertThat(missing.reason())
                .contains("board pack requires opening-balance reconciliation inputs");

        ManagementReportService.PackGenerationResult reconciled =
                service.generatePack(
                        boardPack(
                                List.of(certifiedMember("rep-a", "kpi-cash", "100.00")),
                                "1000.00",
                                "1000.00"));
        assertThat(reconciled.generated()).isTrue();
        assertThat(reconciled.edition().contentHash()).hasSize(64);
    }

    @Test
    void uncertifiedRequiredMemberReportBlocksPackGeneration() {
        ManagementReportService.MemberReport uncertified =
                new ManagementReportService.MemberReport(
                        "rep-north-flash",
                        true,
                        false,
                        List.of(
                                new ManagementReportService.CertifiedFigure(
                                        "fig-cash-north",
                                        "kpi-cash",
                                        new BigDecimal("7"),
                                        ManagementReportService.ActivityState.VALUE)));
        ManagementReportService.PackGenerationResult blocked =
                service.generatePack(
                        boardPack(
                                List.of(certifiedMember("rep-a", "kpi-cash", "7"), uncertified),
                                "10.00",
                                "10.00"));
        assertThat(blocked.generated()).isFalse();
        assertThat(blocked.reason())
                .contains("uncertified required report")
                .contains("rep-north-flash");
    }

    @Test
    void optionalUncertifiedMemberDoesNotBlockButIsRequiredMemberDoes() {
        ManagementReportService.MemberReport optionalUncertified =
                new ManagementReportService.MemberReport("rep-optional", false, false, List.of());
        ManagementReportService.PackGenerationResult ok =
                service.generatePack(
                        boardPack(
                                List.of(
                                        certifiedMember("rep-a", "kpi-cash", "7"),
                                        optionalUncertified),
                                "10.00",
                                "10.00"));
        assertThat(ok.generated()).isTrue();
    }

    @Test
    void regenerationReproducesTheSameEditionContentHash() {
        ManagementReportService.PackRequest request =
                boardPack(
                        List.of(certifiedMember("rep-a", "kpi-cash", "100.00")),
                        "1000.00",
                        "1000.00");
        ManagementReportService.PackEdition first = service.generatePack(request).edition();
        ManagementReportService.ReproductionResult sameInputs = service.reproduce(request, first);
        assertThat(sameInputs.identical()).isTrue();
        assertThat(sameInputs.contentHash()).isEqualTo(first.contentHash());

        ManagementReportService.PackRequest changed =
                boardPack(
                        List.of(certifiedMember("rep-a", "kpi-cash", "100.01")),
                        "1000.00",
                        "1000.00");
        assertThat(service.reproduce(changed, first).identical()).isFalse();
    }

    @Test
    void approvalHistoryIsRecordedAgainstImmutableEditions() {
        ManagementReportService.ApprovalRecord approved =
                service.decide(
                        new ManagementReportService.ApprovalDecision(
                                "pack-board-q1", 1, "cfo@example.invalid", true, "Accepted"),
                        Instant.parse("2026-03-31T20:00:00Z"));
        assertThat(approved.approved()).isTrue();
        assertThat(approved.editionNumber()).isEqualTo(1);

        ManagementReportService.ApprovalRecord rejected =
                service.decide(
                        new ManagementReportService.ApprovalDecision(
                                "pack-board-q1",
                                1,
                                "controller@example.invalid",
                                false,
                                "Commentary gap"),
                        Instant.parse("2026-03-31T21:00:00Z"));
        assertThat(rejected.approved()).isFalse();
        // Both decisions persist as chronology; neither rewrites the edition.
        assertThat(approved.decidedAtUtc()).isBefore(rejected.decidedAtUtc());
    }

    @Test
    void approvalWithoutNamedApproverIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.decide(
                                new ManagementReportService.ApprovalDecision(
                                        "pack-board-q1", 1, " ", true, null),
                                Instant.now()));
    }

    @Test
    void flashAndOperationalLayoutsGenerateWithoutBalanceInputs() {
        ManagementReportService.PackRequest flash =
                new ManagementReportService.PackRequest(
                        "pack-flash",
                        ManagementReportService.Layout.FLASH,
                        "Flash week 13",
                        "fpa@example.invalid",
                        List.of(certifiedMember("rep-north-flash", "kpi-cash", "42.00")),
                        null,
                        null,
                        Instant.parse("2026-03-27T18:00:00Z"),
                        "generator@example.invalid");
        ManagementReportService.PackGenerationResult result = service.generatePack(flash);
        assertThat(result.generated()).isTrue();
        assertThat(result.edition().openingBalance()).isNull();

        ManagementReportService.PackRequest operational =
                new ManagementReportService.PackRequest(
                        "pack-ops",
                        ManagementReportService.Layout.OPERATIONAL,
                        "Operational review",
                        "fpa@example.invalid",
                        List.of(certifiedMember("rep-north-flash", "kpi-revenue", "500.00")),
                        new BigDecimal("10.00"),
                        new BigDecimal("10.00"),
                        Instant.parse("2026-03-27T18:00:00Z"),
                        "generator@example.invalid");
        assertThat(service.generatePack(operational).generated()).isTrue();
    }
}
