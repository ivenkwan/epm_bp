package dev.epm.planning.statements;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task 42 deterministic integrated calculation (rule version icstm-1.0.0): versioned non-driver
 * schedules in, linked P&amp;L / balance-sheet / cash-flow statements out. Zero participates as a
 * value; a null required input is an error and never coerced to zero. The three statements are
 * linked — revenue changes recalculate P&amp;L, cash flow, and the balance sheet together; closing
 * cash always equals opening cash plus the period cash flow; assets equal liabilities plus equity
 * exactly within the declared rounding (scale 2, HALF_UP). Failed calculations never clobber the
 * last valid statement versions. Imbalance blocks submission/approval and carries full coordinates
 * for the imbalance case.
 *
 * <p>Model (all amounts local currency, scale 2 HALF_UP): revenue_total = Σ revenue.*; opex_total =
 * Σ opcost.*; ebit = revenue_total − opex_total; tax = 25% of max(ebit, 0); net_income = ebit −
 * tax; cash_flow = net_income + depreciation − capex; closing_cash = opening.cash + cash_flow;
 * assets = closing_cash + capex − depreciation; equity = opening.equity + net_income; balance
 * difference = assets − equity.
 */
public class IntegratedStatementService {

    /** Governed rule/model versions stamped on every result and case. */
    public static final String RULE_VERSION = "icstm-1.0.0";

    public static final String MODEL_VERSION = "non-driver-integrated-1.0.0";

    private static final BigDecimal TAX_RATE = new BigDecimal("0.25");
    private static final int SCALE = 2;

    /** Calculation status. */
    public enum Status {
        CALCULATED,
        MISSING_REQUIRED_INPUT,
        IMBALANCE
    }

    /** One schedule line; {@code null} value means a required input is missing. */
    public record ScheduleLine(
            String scheduleType, String entityId, String period, String lineKey, BigDecimal value) {

        public String coordinate() {
            return scheduleType + "/" + entityId + "/" + period + "/" + lineKey;
        }
    }

    /** One statement output line with its schedule lineage. */
    public record StatementLine(
            String statementType, String lineKey, BigDecimal amount, String scheduleLineRef) {}

    /** The three linked statements plus the balance check. */
    public record Statements(
            List<StatementLine> profitAndLoss,
            List<StatementLine> balanceSheet,
            List<StatementLine> cashFlow,
            BigDecimal balanceDifference) {}

    /** Full calculation outcome. */
    public record CalculationOutcome(
            Status status,
            Statements statements,
            String errorCoordinate,
            String calcTrace,
            Statements previousValidStatements,
            String note) {

        static CalculationOutcome missing(
                String coordinate, Statements previous, String trace, String note) {
            return new CalculationOutcome(
                    Status.MISSING_REQUIRED_INPUT, null, coordinate, trace, previous, note);
        }
    }

    /**
     * Calculates the linked statements from schedule lines. Deterministic and pure: identical
     * inputs produce identical statements (replay-safe, concurrent-safe). On failure the previous
     * valid statements are preserved untouched.
     */
    public CalculationOutcome calculate(List<ScheduleLine> lines, Statements previousValid) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        Map<String, String> coordinates = new LinkedHashMap<>();
        for (ScheduleLine line : lines) {
            if (line.value() == null) {
                return CalculationOutcome.missing(
                        line.coordinate(),
                        previousValid,
                        "required input " + line.coordinate() + " is null",
                        "null required input is an error and is never coerced to zero");
            }
            values.put(line.lineKey(), line.value());
            coordinates.put(line.lineKey(), line.coordinate());
        }
        List<String> required =
                List.of(
                        "revenue.domestic",
                        "revenue.export",
                        "opcost.salaries",
                        "opcost.other",
                        "capex",
                        "depreciation",
                        "opening.cash",
                        "opening.equity");
        for (String key : required) {
            if (!values.containsKey(key)) {
                return CalculationOutcome.missing(
                        key,
                        previousValid,
                        "required input " + key + " absent from schedules",
                        "required inputs must be present; zero participates, absent does not");
            }
        }

        BigDecimal revenueTotal = values.get("revenue.domestic").add(values.get("revenue.export"));
        BigDecimal opexTotal = values.get("opcost.salaries").add(values.get("opcost.other"));
        BigDecimal ebit = revenueTotal.subtract(opexTotal);
        BigDecimal tax =
                ebit.signum() > 0
                        ? ebit.multiply(TAX_RATE).setScale(SCALE, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
        BigDecimal netIncome = ebit.subtract(tax).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal cashFlow =
                netIncome
                        .add(values.get("depreciation"))
                        .subtract(values.get("capex"))
                        .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal closingCash =
                values.get("opening.cash").add(cashFlow).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal assets =
                closingCash
                        .add(values.get("capex"))
                        .subtract(values.get("depreciation"))
                        .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal equity =
                values.get("opening.equity").add(netIncome).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal difference = assets.subtract(equity).setScale(SCALE, RoundingMode.HALF_UP);

        List<StatementLine> pl =
                List.of(
                        new StatementLine(
                                "PL",
                                "revenue_total",
                                s(revenueTotal),
                                coordinates.get("revenue.domestic")
                                        + ";"
                                        + coordinates.get("revenue.export")),
                        new StatementLine(
                                "PL",
                                "opex_total",
                                s(opexTotal),
                                coordinates.get("opcost.salaries")
                                        + ";"
                                        + coordinates.get("opcost.other")),
                        new StatementLine("PL", "ebit", s(ebit), "derived"),
                        new StatementLine("PL", "tax", s(tax), "derived:25pct"),
                        new StatementLine("PL", "net_income", s(netIncome), "derived"));
        List<StatementLine> bs =
                List.of(
                        new StatementLine(
                                "BS",
                                "closing_cash",
                                s(closingCash),
                                coordinates.get("opening.cash") + ";cash_flow"),
                        new StatementLine(
                                "BS",
                                "assets_total",
                                s(assets),
                                coordinates.get("capex") + ";" + coordinates.get("depreciation")),
                        new StatementLine(
                                "BS", "liabilities_total", BigDecimal.ZERO.setScale(SCALE), "none"),
                        new StatementLine(
                                "BS", "equity_total", s(equity), coordinates.get("opening.equity")),
                        new StatementLine("BS", "balance_difference", s(difference), "check"));
        List<StatementLine> cf =
                List.of(
                        new StatementLine("CF", "net_income", s(netIncome), "from PL"),
                        new StatementLine(
                                "CF",
                                "depreciation",
                                s(values.get("depreciation")),
                                coordinates.get("depreciation")),
                        new StatementLine(
                                "CF", "capex", s(values.get("capex")), coordinates.get("capex")),
                        new StatementLine("CF", "cash_flow_total", s(cashFlow), "derived"));

        Statements statements = new Statements(pl, bs, cf, difference);
        String trace =
                "model="
                        + MODEL_VERSION
                        + " rule="
                        + RULE_VERSION
                        + " revenue="
                        + s(revenueTotal)
                        + " opex="
                        + s(opexTotal)
                        + " ebit="
                        + s(ebit)
                        + " tax="
                        + s(tax)
                        + " net_income="
                        + s(netIncome)
                        + " cash_flow="
                        + s(cashFlow)
                        + " closing_cash="
                        + s(closingCash)
                        + " assets="
                        + s(assets)
                        + " equity="
                        + s(equity)
                        + " difference="
                        + s(difference);
        if (difference.signum() != 0) {
            return new CalculationOutcome(
                    Status.IMBALANCE,
                    statements,
                    "BS/balance_difference",
                    trace,
                    previousValid,
                    "integrated statements do not balance — submission and approval are blocked");
        }
        return new CalculationOutcome(
                Status.CALCULATED,
                statements,
                null,
                trace,
                null,
                "balanced within scale-2 HALF_UP");
    }

    /** Cash-to-cash-flow linkage check: closing cash equals opening cash plus flow. */
    public boolean cashLinkageHolds(Statements statements, BigDecimal openingCash) {
        BigDecimal closing = line(statements.balanceSheet(), "closing_cash").amount();
        BigDecimal flow = line(statements.cashFlow(), "cash_flow_total").amount();
        return closing.compareTo(openingCash.add(flow).setScale(SCALE, RoundingMode.HALF_UP)) == 0;
    }

    private static StatementLine line(List<StatementLine> lines, String key) {
        return lines.stream().filter(l -> l.lineKey().equals(key)).findFirst().orElseThrow();
    }

    private static BigDecimal s(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Resolves the statement line at drill grain (schedule-to-statement lineage). */
    public List<String> drillLineage(Statements statements, String statementLineKey) {
        List<String> refs = new ArrayList<>();
        for (List<StatementLine> statement :
                List.of(
                        statements.profitAndLoss(),
                        statements.balanceSheet(),
                        statements.cashFlow())) {
            for (StatementLine line : statement) {
                if (line.lineKey().equals(statementLineKey) && line.scheduleLineRef() != null) {
                    refs.addAll(List.of(line.scheduleLineRef().split(";")));
                }
            }
        }
        return refs;
    }
}
