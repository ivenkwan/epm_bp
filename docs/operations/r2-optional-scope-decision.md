# R2 Optional-Scope Decision: Stock/Unit Liquidity Context (F-022)

Decision: **DEFERRED (default) — feature disabled pending a signed activation.**

R2 mandatory acceptance does not depend on F-022 (it is an optional capability in the
plan: "May run only after mandatory R2 controls remain green"). Until a named business
owner signs activation, the feature stays disabled by construction — every read path
throws while disabled, the browser surface renders the disabled state, and no
integration feeds the tables.

## What was delivered (dormant, zero impact on mandatory scope)

- `V026__stock_unit_liquidity.sql`: optional read-model table (quantities, ageing
  buckets, classifications, movement refs, optional remarks, source revision +
  freshness) with an inert default (`certified = false`).
- `StockUnitContextService`: strict read-model semantics — zero renders as zero, blank
  remarks stay blank, missing quantity never becomes zero (upstream quarantine), no
  liquidity/cash inference, no effect on the certified cash position; entitlement-denied
  detail returns empty without leakage; activation is an explicit owner act.
- `StockUnitContextTest` 5/5 (fail-closed default, zero/blank semantics, source +
  freshness labels, reconciliation to submitted detail, denied scope).
- e2e `stock-unit-context.spec.ts`: disabled state visible; no figures leak while
  disabled.

## To activate (owner checklist)

1. Authorize a source and named owner for stock/unit quantities, ageing, movements,
   classifications, and optional allocations (a contracted source, Task 10).
2. Record the activation decision here (business owner + date) and run the enabled
   acceptance: AC-F-022-01 plus entitlement/reconciliation suites with results
   attached to the R2 evidence pack.
3. If deferred indefinitely, this record is the signed deferral the plan requires.

Activation record: ____________ (business owner, date) — or signed deferral:
____________.
