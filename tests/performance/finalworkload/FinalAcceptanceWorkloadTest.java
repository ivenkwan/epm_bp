package tests.performance.finalworkload;

import static org.assertj.core.api.Assertions.assertThat;

import dev.epm.planning.statements.IntegratedStatementService;
import dev.epm.reporting.dashboards.SelfAnalysisService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 56 final acceptance workload shape (maps the plan's tests/performance/final/
 * entry point; the directory is finalworkload because `final` is a Java reserved
 * word): a 5,000-line integrated planning model calculates to balanced statements
 * and reconciles exactly; a 20,000-row self-service drill pages under the governed
 * cap with exact totals. These structural workloads precondition the signed 2×-peak
 * performance runs that execute against the confirmed population.
 */
class FinalAcceptanceWorkloadTest {

    @Test
    void fiveThousandLineIntegratedModelBalancesExactly() {
        IntegratedStatementService engine = new IntegratedStatementService();
        // 625 entity models × 8 schedule lines = 5,000 lines through the scale-2
        // engine; every entity uses the §8 proportions so each balances exactly.
        BigDecimal aggregateNetIncome = BigDecimal.ZERO;
        for (int entity = 0; entity < 625; entity++) {
            String id = "E" + entity;
            List<IntegratedStatementService.ScheduleLine> lines =
                    List.of(
                            line("REVENUE", id, "revenue.domestic", "1000.00"),
                            line("REVENUE", id, "revenue.export", "500.00"),
                            line("OPERATING_COST", id, "opcost.salaries", "400.00"),
                            line("OPERATING_COST", id, "opcost.other", "100.00"),
                            line("CAPITAL", id, "capex", "300.00"),
                            line("CAPITAL", id, "depreciation", "50.00"),
                            line("OPENING_BALANCE", id, "opening.cash", "2000.00"),
                            line("OPENING_BALANCE", id, "opening.equity", "2000.00"));
            IntegratedStatementService.CalculationOutcome outcome = engine.calculate(lines, null);
            assertThat(outcome.status()).isEqualTo(IntegratedStatementService.Status.CALCULATED);
            assertThat(outcome.statements().balanceDifference()).isEqualByComparingTo("0.00");
            aggregateNetIncome =
                    aggregateNetIncome.add(
                            outcome.statements().profitAndLoss().stream()
                                    .filter(l -> l.lineKey().equals("net_income"))
                                    .findFirst()
                                    .orElseThrow()
                                    .amount());
        }
        // Aggregate net income: 625 entities × 750.00.
        assertThat(aggregateNetIncome)
                .isEqualByComparingTo(new BigDecimal("750.00").multiply(BigDecimal.valueOf(625)));
    }

    @Test
    void twentyThousandRowDrillReconcilesUnderGovernedPagination() {
        SelfAnalysisService service = new SelfAnalysisService();
        var rows = new ArrayList<SelfAnalysisService.DetailRow>();
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < 20_000; i++) {
            rows.add(new SelfAnalysisService.DetailRow("ENT-101", "m" + i, BigDecimal.ONE));
            sum = sum.add(BigDecimal.ONE);
        }
        SelfAnalysisService.KpiTotal kpi =
                new SelfAnalysisService.KpiTotal("kpi-cash", "ed-final", sum, "ENTITY", true);
        long materialized = 0;
        for (int page = 0; ; page++) {
            var result = service.drill(kpi, rows, "ENTITY", List.of("ENT-101"), page, 500);
            if (result.rows().isEmpty()) {
                break;
            }
            materialized += result.rows().size();
            assertThat(result.rows().size()).isLessThanOrEqualTo(500);
            if (page > 45) {
                throw new AssertionError("pagination did not terminate");
            }
        }
        assertThat(materialized).isEqualTo(20_000);
        assertThat(service.reconcile(kpi, rows).reconciled()).isTrue();
    }

    private static IntegratedStatementService.ScheduleLine line(
            String type, String entity, String key, String value) {
        return new IntegratedStatementService.ScheduleLine(
                type, entity, "2027-P01", key, new BigDecimal(value));
    }
}
