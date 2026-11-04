# Variance and KPI Planning (Task 44)

Deterministic variance calculation and shared-KPI planning on the **certified semantic
catalog**: actuals versus Budget / Forecast / prior-period plans, always on the
catalog's shared formula version.

## Objects

| Object | Meaning |
|---|---|
| `epm_kpi_plan_value` | Planned KPI values with `formula_version` and explicit `source_ref` lineage |
| `epm_variance_result` | Variance lines with basis, amount, percentage + `percentage_state`, certification flag, formula version |
| `epm_variance_commentary` | Commentary with EXPLAIN / MONITOR / FOLLOW_UP actions and named owners |

## Governed semantics

1. **Amount variance** = actual − plan, scale 2 HALF_UP, decimal-string end-to-end.
2. **Percentage variance** = amount ÷ \|plan\|, scale 6 HALF_UP. A **zero baseline is
   UNDEFINED** (`UNDEFINED_ZERO_BASELINE`, null percentage) — never infinite, never 0.
3. **Uncertified actual scope cannot enter certified comparison** — the result is
   `UNAVAILABLE` with an explicit note, not an exception-free figure.
4. **Null values are unavailable results** — never coerced to zero.
5. **Shared formula version** — actual and plan must carry the same catalog formula
   version; mixed versions refuse with a governed unavailable outcome.
6. **Drill reconciliation** — a line's amount variance must equal the exact sum of its
   component variances (verified by `reconcile`).
7. **Planned KPI values** carry the same formula version as actuals plus explicit
   source/version lineage; a KPI without a certified formula version cannot be planned.
8. **Entitlement preservation** — variance views label unentitled dimensions
   unavailable; they never leak figures.

## Worked examples (golden arithmetic)

- actual 1100.00 vs budget 1000.00 → amount +100.00, percentage +0.100000
- actual 950.00 vs budget 1000.00 → amount −50.00, percentage −0.050000
- actual 50.00 vs budget 0.00 → amount +50.00, percentage **undefined (zero baseline)**
- drill: +100.00 line = components 80.00 + 20.00 exactly
