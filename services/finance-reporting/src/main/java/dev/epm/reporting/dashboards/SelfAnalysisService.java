package dev.epm.reporting.dashboards;

import java.math.BigDecimal;
import java.util.List;

/**
 * Task 47 governed self-service analysis: saved views with authorized filters, restricted-entity
 * denial (explicit, never silent filtering), drill refusal below the source grain, stale-edition
 * detection, governed pagination for large result sets, and filtered-detail reconciliation — the
 * sum of every authorized detail row must equal the starting KPI total. No KPI is redefined
 * locally; cached results never leak across entitlement scopes.
 */
public class SelfAnalysisService {

    /** One saved analysis view with its authorized entity scope. */
    public record SavedView(String viewId, String name, String owner, List<String> entityScope) {}

    /** A KPI total on a certified edition, at its governed grain. */
    public record KpiTotal(
            String kpiId,
            String editionId,
            BigDecimal total,
            String grain,
            boolean editionCurrent) {}

    /** One authorized detail row at the governed grain. */
    public record DetailRow(String entity, String member, BigDecimal amount) {}

    /** Saved-view load outcome (entitlement re-checked, denials listed). */
    public record ViewLoad(SavedView view, List<String> visible, List<String> denied) {}

    /** Loads a saved view against the actor's current entitlements. */
    public ViewLoad loadView(SavedView view, List<String> entitled) {
        return new ViewLoad(
                view,
                view.entityScope().stream().filter(entitled::contains).toList(),
                view.entityScope().stream()
                        .filter(e -> !entitled.contains(e))
                        .map(e -> "restricted entity denied: " + e)
                        .toList());
    }

    /** Outcome of a drill request. */
    public record DrillPage(
            boolean granted,
            String reason,
            List<DetailRow> rows,
            int page,
            int pageSize,
            long totalRows,
            boolean truncated) {}

    /**
     * Drills a KPI into detail rows: refuses stale editions and requests below the source grain;
     * large sets page under the governed page cap with an explicit truncation flag (never a silent
     * cut).
     */
    public DrillPage drill(
            KpiTotal kpi,
            List<DetailRow> allDetail,
            String requestedGrain,
            List<String> entitledEntities,
            int page,
            int pageSizeCap) {
        if (!kpi.editionCurrent()) {
            return new DrillPage(
                    false, "stale edition — refresh before drilling", List.of(), 0, 0, 0, false);
        }
        if (!requestedGrain.equals(kpi.grain())) {
            return new DrillPage(
                    false,
                    "grain below governed source ("
                            + requestedGrain
                            + " < "
                            + kpi.grain()
                            + ") — detail never fabricated",
                    List.of(),
                    0,
                    0,
                    0,
                    false);
        }
        List<DetailRow> authorized =
                allDetail.stream().filter(row -> entitledEntities.contains(row.entity())).toList();
        long total = authorized.size();
        int size = Math.max(1, Math.min(pageSizeCap, 500));
        int from = Math.max(0, page) * size;
        List<DetailRow> pageRows =
                from >= authorized.size()
                        ? List.of()
                        : authorized.subList(from, Math.min(authorized.size(), from + size));
        return new DrillPage(true, null, pageRows, Math.max(0, page), size, total, total > size);
    }

    /** Reconciliation: authorized detail must sum exactly to the starting KPI total. */
    public record Reconciliation(
            boolean reconciled, BigDecimal detailSum, BigDecimal kpiTotal, String note) {}

    public Reconciliation reconcile(KpiTotal kpi, List<DetailRow> authorizedDetail) {
        if (authorizedDetail.isEmpty()) {
            return new Reconciliation(
                    false,
                    BigDecimal.ZERO,
                    kpi.total(),
                    "empty dataset: no certified detail to reconcile");
        }
        BigDecimal sum =
                authorizedDetail.stream()
                        .map(DetailRow::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean ok = sum.compareTo(kpi.total()) == 0;
        return new Reconciliation(
                ok,
                sum,
                kpi.total(),
                ok
                        ? "filtered detail reconciles to the starting KPI"
                        : "filtered detail does NOT reconcile: "
                                + sum
                                + " vs "
                                + kpi.total()
                                + " — scope or entitlement changed under the result");
    }
}
