package dev.epm.reporting.reports;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task 39 management-report governance: report/pack configurations assemble from certified data
 * products and shared KPI figures only — no report-local KPI calculation exists. Certification
 * fails closed on uncertified required scope, retired/unknown KPI references, empty required
 * sections, and missing data inside required sections (legitimate zero activity stays a governed
 * value). Pack generation preserves each pack's independent immutable edition plus approval
 * history; regeneration from the same inputs reproduces the edition byte-for-byte via a
 * deterministic content hash. Opening balances must reconcile to the prior certified closing before
 * a pack edition can be generated for balance-carrying layouts.
 */
public class ManagementReportService {

    /** Reports reference the shared semantic catalog; they never re-derive figures. */
    public enum KpiCatalogStatus {
        CERTIFIED,
        RETIRED,
        UNKNOWN
    }

    public enum Scope {
        ENTERPRISE,
        DIVISION,
        ENTITY
    }

    public enum Layout {
        FLASH,
        OPERATIONAL,
        BOARD
    }

    /** Governed activity semantics: zero is a value, absence is missing data. */
    public enum ActivityState {
        VALUE,
        ZERO_ACTIVITY,
        MISSING
    }

    /** Request shape for one report definition (Task 39 governed configuration). */
    public record SectionDefinition(
            String sectionId,
            String title,
            boolean required,
            String dataProduct,
            String comparison) {}

    /** One report as configured for certification. */
    public record ReportDefinition(
            String reportId,
            Scope scope,
            String divisionId,
            String entityId,
            String title,
            Layout layout,
            List<SectionDefinition> sections,
            String owner,
            int reportVersion) {}

    /** A figure as supplied from the certified data product (no local recalculation). */
    public record FigureInput(
            String sectionId, String figureId, String kpiId, BigDecimal amount, boolean present) {}

    /** Catalog + scope certification inputs; both come from governed ledgers. */
    public record KpiReference(String kpiId, KpiCatalogStatus status) {}

    /** Certification state of a division/entity scope from the certification ledger. */
    public record ScopeCertification(String scopeKey, boolean certified) {}

    /** Certified figure as persisted (amount plus governed activity state). */
    public record CertifiedFigure(
            String figureId, String kpiId, BigDecimal amount, ActivityState state) {}

    /** Result of report certification. */
    public record CertificationResult(
            String reportId, boolean certified, String reason, List<CertifiedFigure> figures) {
        static CertificationResult blocked(String reportId, String reason) {
            return new CertificationResult(reportId, false, reason, List.of());
        }
    }

    /** A member report offered to a pack, with its certification outcome attached. */
    public record MemberReport(
            String reportId, boolean required, boolean certified, List<CertifiedFigure> figures) {}

    /** Pack generation request including reconciliation inputs. */
    public record PackRequest(
            String packId,
            Layout layout,
            String title,
            String owner,
            List<MemberReport> members,
            BigDecimal openingBalance,
            BigDecimal priorClosingBalance,
            Instant asOfUtc,
            String generatedBy) {}

    /** An immutable pack edition; content hash makes reproduction verifiable. */
    public record PackEdition(
            String packId,
            int editionNumber,
            String contentHash,
            BigDecimal openingBalance,
            BigDecimal priorClosingBalance,
            Instant asOfUtc,
            String generatedBy,
            List<MemberReport> members) {}

    /** Result of pack generation. */
    public record PackGenerationResult(
            String packId, boolean generated, String reason, PackEdition edition) {
        static PackGenerationResult blocked(String packId, String reason) {
            return new PackGenerationResult(packId, false, reason, null);
        }
    }

    /** Approval decision recorded against one immutable edition. */
    public record ApprovalDecision(
            String packId, int editionNumber, String approver, boolean approved, String comment) {}

    /** Approval chronology entry (order preserved; never rewritten). */
    public record ApprovalRecord(
            String packId,
            int editionNumber,
            String approver,
            boolean approved,
            Instant decidedAtUtc) {}

    /** Reproduction outcome: same inputs must yield the same hash and figures. */
    public record ReproductionResult(
            String packId, int editionNumber, boolean identical, String contentHash) {}

    /**
     * Certifies one management report. Fail-closed order: layout validity, scope certification,
     * retired/unknown KPI references, empty required sections, then missing data inside required
     * sections. Zero amounts certify as ZERO_ACTIVITY.
     */
    public CertificationResult certifyReport(
            ReportDefinition report,
            List<FigureInput> figures,
            List<KpiReference> catalog,
            List<ScopeCertification> scopeCertifications) {
        if (report == null || report.layout() == null) {
            return CertificationResult.blocked(null, "invalid layout reference");
        }
        if (report.sections() == null || report.sections().isEmpty()) {
            return CertificationResult.blocked(report.reportId(), "report has no sections");
        }
        for (SectionDefinition section : report.sections()) {
            if (section.comparison() != null
                    && !List.of("PRIOR_PERIOD", "BUDGET", "FORECAST")
                            .contains(section.comparison())) {
                return CertificationResult.blocked(
                        report.reportId(),
                        "invalid comparison reference in section " + section.sectionId());
            }
        }
        String scopeKey = scopeKey(report);
        boolean scopeCertified =
                scopeCertifications.stream()
                        .anyMatch(s -> s.scopeKey().equals(scopeKey) && s.certified());
        if (!scopeCertified) {
            return CertificationResult.blocked(
                    report.reportId(), "required scope uncertified: " + scopeKey);
        }
        Map<String, KpiReference> catalogById = new LinkedHashMap<>();
        for (KpiReference reference : catalog) {
            catalogById.put(reference.kpiId(), reference);
        }
        List<CertifiedFigure> certifiedFigures = new ArrayList<>();
        for (FigureInput figure : figures) {
            KpiReference reference = catalogById.get(figure.kpiId());
            if (reference == null || reference.status() != KpiCatalogStatus.CERTIFIED) {
                String state = reference == null ? "unknown" : reference.status().name();
                return CertificationResult.blocked(
                        report.reportId(),
                        "retired or unknown KPI reference " + figure.kpiId() + " (" + state + ")");
            }
            ActivityState state;
            if (!figure.present()) {
                state = ActivityState.MISSING;
            } else if (figure.amount().signum() == 0) {
                state = ActivityState.ZERO_ACTIVITY;
            } else {
                state = ActivityState.VALUE;
            }
            certifiedFigures.add(
                    new CertifiedFigure(figure.figureId(), figure.kpiId(), figure.amount(), state));
        }
        for (SectionDefinition section : report.sections()) {
            List<FigureInput> sectionFigures =
                    figures.stream()
                            .filter(f -> f.sectionId().equals(section.sectionId()))
                            .toList();
            if (section.required() && sectionFigures.isEmpty()) {
                return CertificationResult.blocked(
                        report.reportId(), "empty required section " + section.sectionId());
            }
            boolean missingInRequired = sectionFigures.stream().anyMatch(f -> !f.present());
            if (section.required() && missingInRequired) {
                return CertificationResult.blocked(
                        report.reportId(),
                        "missing data in required section " + section.sectionId());
            }
        }
        certifiedFigures.sort(Comparator.comparing(CertifiedFigure::figureId));
        return new CertificationResult(
                report.reportId(), true, null, List.copyOf(certifiedFigures));
    }

    /**
     * Generates one immutable pack edition. Fail-closed: members exist, required member reports are
     * certified, shared figures agree across reports (same KPI, same value — divergence proves
     * report-local recalculation), and the opening balance reconciles to the prior certified
     * closing for balance-carrying layouts.
     */
    public PackGenerationResult generatePack(PackRequest request) {
        if (request.members() == null || request.members().isEmpty()) {
            return PackGenerationResult.blocked(request.packId(), "pack has no member reports");
        }
        for (MemberReport member : request.members()) {
            if (member.required() && !member.certified()) {
                return PackGenerationResult.blocked(
                        request.packId(),
                        "uncertified required report in pack: " + member.reportId());
            }
        }
        Map<String, BigDecimal> sharedFigures = new LinkedHashMap<>();
        for (MemberReport member : request.members()) {
            for (CertifiedFigure figure : member.figures()) {
                BigDecimal existing = sharedFigures.get(figure.kpiId());
                if (existing != null && existing.compareTo(figure.amount()) != 0) {
                    return PackGenerationResult.blocked(
                            request.packId(),
                            "shared figure divergence for KPI "
                                    + figure.kpiId()
                                    + " between member reports");
                }
                sharedFigures.putIfAbsent(figure.kpiId(), figure.amount());
            }
        }
        if (request.layout() == Layout.BOARD) {
            if (request.openingBalance() == null || request.priorClosingBalance() == null) {
                return PackGenerationResult.blocked(
                        request.packId(),
                        "board pack requires opening-balance reconciliation inputs");
            }
            if (request.openingBalance().compareTo(request.priorClosingBalance()) != 0) {
                return PackGenerationResult.blocked(
                        request.packId(), "opening balance does not reconcile to prior closing");
            }
        } else if (request.openingBalance() != null
                && request.priorClosingBalance() != null
                && request.openingBalance().compareTo(request.priorClosingBalance()) != 0) {
            return PackGenerationResult.blocked(
                    request.packId(), "opening balance does not reconcile to prior closing");
        }
        PackEdition edition =
                new PackEdition(
                        request.packId(),
                        1,
                        contentHash(request),
                        request.openingBalance(),
                        request.priorClosingBalance(),
                        request.asOfUtc(),
                        request.generatedBy(),
                        List.copyOf(request.members()));
        return new PackGenerationResult(request.packId(), true, null, edition);
    }

    /**
     * Regenerates the pack and compares against an existing edition: identical inputs must
     * reproduce the same content hash (output equivalence), independent of edition number and
     * approval history.
     */
    public ReproductionResult reproduce(PackRequest request, PackEdition existing) {
        PackGenerationResult regenerated = generatePack(request);
        if (!regenerated.generated()) {
            return new ReproductionResult(
                    request.packId(), existing.editionNumber(), false, regenerated.reason());
        }
        boolean identical =
                regenerated.edition().contentHash().equals(existing.contentHash())
                        && regenerated.edition().members().size() == existing.members().size();
        return new ReproductionResult(
                request.packId(),
                existing.editionNumber(),
                identical,
                regenerated.edition().contentHash());
    }

    /** Records an approval decision; editions themselves are never modified. */
    public ApprovalRecord decide(ApprovalDecision decision, Instant decidedAtUtc) {
        if (decision.approver() == null || decision.approver().isBlank()) {
            throw new IllegalArgumentException("approval requires a named approver");
        }
        return new ApprovalRecord(
                decision.packId(),
                decision.editionNumber(),
                decision.approver(),
                decision.approved(),
                decidedAtUtc);
    }

    private static String scopeKey(ReportDefinition report) {
        return switch (report.scope()) {
            case ENTERPRISE -> "ENTERPRISE";
            case DIVISION -> "DIVISION:" + report.divisionId();
            case ENTITY -> "ENTITY:" + report.entityId();
        };
    }

    /** Deterministic content hash over the governed edition inputs (reproducible). */
    private static String contentHash(PackRequest request) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(request.packId()).append('|');
        canonical.append(request.layout()).append('|');
        canonical.append(request.owner()).append('|');
        append(canonical, request.openingBalance());
        canonical.append('~');
        append(canonical, request.priorClosingBalance());
        canonical.append('~');
        request.members().stream()
                .sorted(Comparator.comparing(MemberReport::reportId))
                .forEach(
                        member -> {
                            canonical.append(member.reportId()).append(':');
                            member.figures().stream()
                                    .sorted(Comparator.comparing(CertifiedFigure::figureId))
                                    .forEach(
                                            figure -> {
                                                canonical
                                                        .append(figure.figureId())
                                                        .append('=')
                                                        .append(figure.kpiId())
                                                        .append('@');
                                                append(canonical, figure.amount());
                                                canonical
                                                        .append('/')
                                                        .append(figure.state())
                                                        .append(';');
                                            });
                        });
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void append(StringBuilder target, BigDecimal value) {
        target.append(value == null ? "" : value.stripTrailingZeros().toPlainString());
    }
}
