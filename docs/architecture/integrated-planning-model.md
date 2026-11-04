# Integrated Planning Model (Task 42)

Versioned **non-driver input schedules** feed one deterministic integrated calculation
that produces linked **P&L, balance sheet, and cash flow** statements. Rule version
`icstm-1.0.0`; model version `non-driver-integrated-1.0.0`; all arithmetic BigDecimal
scale-2 HALF_UP, decimal-string end-to-end. The golden set is
`db/fixtures/expected/expected-integrated-statements.csv` (§8 hand calculations).

## Objects

| Object | Meaning |
|---|---|
| `epm_input_schedule` | Versioned schedule per plan (REVENUE / OPERATING_COST / CAPITAL / WORKFORCE / OPENING_BALANCE) |
| `epm_schedule_line` | Input cells; **NULL value = required input missing** (zero is a value) |
| `epm_statement_result` | Statement run stamped with model + rule versions and a calculation trace |
| `epm_statement_line` | Output lines with schedule-to-statement lineage refs |
| `epm_imbalance_case` | Imbalance record with statement/period/entity/schedule/difference/versions/trace |

## Model (icstm-1.0.0)

```
revenue_total = Σ revenue.*            opex_total = Σ opcost.*
ebit        = revenue_total − opex_total
tax         = 25% × max(ebit, 0)       net_income = ebit − tax
cash_flow   = net_income + depreciation − capex
closing_cash = opening.cash + cash_flow           (cash-to-cash-flow linkage)
assets      = closing_cash + capex − depreciation
equity      = opening.equity + net_income         (liabilities = 0 in this set)
check       = assets − (liabilities + equity) = 0.00   (exact, within declared rounding)
```

The three statements are **linked**: any schedule change recalculates P&L, CF, and BS
together in one deterministic pass (pure function — replay and concurrent runs are
identical).

## Governed semantics

1. **Zero participates** — `0.00` is a legitimate input and output value.
2. **Null is an error** — a null required input yields `MISSING_REQUIRED_INPUT` with
   the exact coordinate (`schedule/entity/period/line`); never coerced to zero.
3. **Failures preserve state** — a failed calculation returns the previous valid
   statement versions untouched.
4. **Imbalance blocks** — submission and approval are refused; an imbalance case opens
   with full coordinates (statement, period, entity, schedule, difference,
   model/rule versions, calculation trace).
5. **Case closure is bookkeeping** — closing an imbalance case never approves or
   publishes the plan; the plan must independently re-enter its own approval chain
   with a balanced calculation.
6. **No browser-authoritative formulas** — the browser renders statements and drill
   lineage only; all calculation is server-side with stamped versions.

The governed policy is also encoded in `rules/dmn/planning/integrated-statements.dmn`
(`MISSING_REQUIRED_INPUT` / `IMBALANCE` / `PASS`), kept in agreement with the engine by
test.
