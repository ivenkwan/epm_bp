package tests.performance.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import dev.epm.reporting.dashboards.SelfAnalysisService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 47 dashboard/drill load shape: 20,000-row drill pages under the governed cap
 * with exact totals, bounded page materialization, and deterministic reconciliation —
 * the structural precondition for the signed p95 dashboard/drill/pack workloads
 * (which execute against the confirmed population and attach separately).
 */
class DashboardDrillLoadTest {

    @Test
    void twentyThousandRowDrillPagesBoundedAndReconciles() {
        SelfAnalysisService service = new SelfAnalysisService();
        var rows = new ArrayList<SelfAnalysisService.DetailRow>();
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < 20_000; i++) {
            rows.add(new SelfAnalysisService.DetailRow("ENT-101", "m" + i, BigDecimal.ONE));
            sum = sum.add(BigDecimal.ONE);
        }
        SelfAnalysisService.KpiTotal kpi =
                new SelfAnalysisService.KpiTotal("kpi-cash", "ed-10", sum, "ENTITY", true);

        long materialized = 0;
        for (int page = 0; ; page++) {
            SelfAnalysisService.DrillPage result =
                    service.drill(kpi, rows, "ENTITY", List.of("ENT-101"), page, 500);
            assertThat(result.granted()).isTrue();
            assertThat(result.rows().size()).isLessThanOrEqualTo(500);
            if (result.rows().isEmpty()) {
                break;
            }
            materialized += result.rows().size();
            if (page > 45) {
                throw new AssertionError("pagination did not terminate");
            }
        }
        assertThat(materialized).isEqualTo(20_000);
        assertThat(
                        service.reconcile(kpi, rows).reconciled())
                .as("20k-row drill reconciles exactly to its KPI total")
                .isTrue();
    }
}
