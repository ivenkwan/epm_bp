package dev.epm.reporting.dashboards;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 47 self-service acceptance: restricted-entity filters denied explicitly, drill refused below
 * source grain and on stale editions, governed pagination for large sets, empty datasets flagged,
 * and every filtered detail total reconciling to its starting KPI (entitlement-change-safe).
 */
class SelfAnalysisServiceTest {

    private final SelfAnalysisService service = new SelfAnalysisService();

    private static final SelfAnalysisService.KpiTotal CASH =
            new SelfAnalysisService.KpiTotal(
                    "kpi-cash", "ed-10", new BigDecimal("3000.00"), "ENTITY", true);

    private static List<SelfAnalysisService.DetailRow> detail() {
        return List.of(
                new SelfAnalysisService.DetailRow("ENT-101", "north", new BigDecimal("1000.00")),
                new SelfAnalysisService.DetailRow("ENT-102", "south", new BigDecimal("1500.00")),
                new SelfAnalysisService.DetailRow("ENT-103", "east", new BigDecimal("500.00")));
    }

    @Test
    void savedViewsDenyRestrictedEntitiesExplicitly() {
        SelfAnalysisService.ViewLoad load =
                service.loadView(
                        new SelfAnalysisService.SavedView(
                                "view-1",
                                "Group cash",
                                "cfo@epm",
                                List.of("ENT-101", "ENT-102", "ENT-999")),
                        List.of("ENT-101", "ENT-102"));
        assertThat(load.visible()).containsExactly("ENT-101", "ENT-102");
        assertThat(load.denied()).containsExactly("restricted entity denied: ENT-999");
    }

    @Test
    void drillRefusesBelowSourceGrainAndStaleEditions() {
        SelfAnalysisService.DrillPage belowGrain =
                service.drill(CASH, detail(), "ACCOUNT", List.of("ENT-101"), 0, 100);
        assertThat(belowGrain.granted()).isFalse();
        assertThat(belowGrain.reason()).contains("never fabricated");

        SelfAnalysisService.KpiTotal stale =
                new SelfAnalysisService.KpiTotal(
                        "kpi-cash", "ed-9", new BigDecimal("3000.00"), "ENTITY", false);
        assertThat(service.drill(stale, detail(), "ENTITY", List.of("ENT-101"), 0, 100).granted())
                .isFalse();
        assertThat(service.drill(stale, detail(), "ENTITY", List.of("ENT-101"), 0, 100).reason())
                .contains("stale edition");
    }

    @Test
    void largeResultSetsPageUnderTheGovernedCapWithTruncationFlag() {
        var many = new java.util.ArrayList<SelfAnalysisService.DetailRow>();
        for (int i = 0; i < 2500; i++) {
            many.add(new SelfAnalysisService.DetailRow("ENT-101", "m" + i, BigDecimal.ONE));
        }
        SelfAnalysisService.DrillPage page =
                service.drill(CASH, many, "ENTITY", List.of("ENT-101"), 0, 10_000);
        assertThat(page.pageSize()).as("page size capped at 500").isEqualTo(500);
        assertThat(page.rows()).hasSize(500);
        assertThat(page.totalRows()).isEqualTo(2500);
        assertThat(page.truncated()).isTrue();
        assertThat(service.drill(CASH, many, "ENTITY", List.of("ENT-101"), 5, 10_000).rows())
                .isEmpty();
    }

    @Test
    void emptyDatasetsAreFlaggedNotFabricated() {
        SelfAnalysisService.Reconciliation empty = service.reconcile(CASH, List.of());
        assertThat(empty.reconciled()).isFalse();
        assertThat(empty.note()).contains("empty dataset");
    }

    @Test
    void filteredDetailReconcilesExactlyToTheStartingKpi() {
        SelfAnalysisService.Reconciliation full = service.reconcile(CASH, detail());
        assertThat(full.reconciled()).isTrue();
        assertThat(full.detailSum()).isEqualByComparingTo("3000.00");

        // An entitlement change under the same result must surface as a break, not a wrong number.
        SelfAnalysisService.Reconciliation partial =
                service.reconcile(
                        CASH, detail().stream().filter(r -> r.entity().equals("ENT-101")).toList());
        assertThat(partial.reconciled()).isFalse();
        assertThat(partial.note()).contains("does NOT reconcile");
    }
}
