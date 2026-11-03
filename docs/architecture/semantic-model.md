# Semantic Model

Certified KPI and semantic catalog (Task 12; `db/migrations/V008__semantic_catalog.sql`).

## KPI card (`epm_kpi_card`)

Every card carries the full governance record: stable `kpi_id`, `formula` (an
allowlisted expression id — never user code), `unit` (MONEY/RATIO/...), `grain`,
`dimensions`, named `owner`, `cadence`, `sensitivity` (PUBLIC/INTERNAL/RESTRICTED),
`source_mappings` (measure → source field), `thresholds`, a mandatory `worked_example`,
`drill_policy`, `version`, and the lifecycle `status` with `certified_by`/`certified_at`.

## Allowlisted expressions (`epm_semantic_expression`)

Registered identifiers only: `sum`, `gearing`, `headroom`, `one`. The evaluator
(`KpiEvaluator`) resolves the identifier; arbitrary expressions are structurally
impossible.

## Determinism and governed unavailability

- Same card + same inputs → identical value and version, every time
  (BigDecimal, scale 6 for ratios, HALF_UP).
- Zero denominator → `UNAVAILABLE_ZERO_DENOMINATOR` (value stays null; never 0).
- Null numerator → `UNAVAILABLE_NULL_NUMERATOR` (never coerced to zero).
- Absent mapped measure → `UNDEFINED_MEASURE` (never guessed).
- Every evaluation (OK and unavailable) appends to `epm_kpi_evaluation` with its input
  snapshot, result, status, and UTC time.

## Lifecycle

`DRAFT → TESTED → CERTIFIED → DEPRECATED` (`KpiCatalogService`):

1. Proposal requires a named owner and a worked example.
2. Certification requires: TESTED status, an independent certifier (the owner cannot
   certify their own KPI), and a worked example that evaluates OK at the same version.
3. Deprecated cards stop being evaluated; certified historical results remain
   reproducible from their recorded snapshots.

## DMN asset

`rules/dmn/kpi/ratio-availability.dmn` encodes the availability decision table
(absent vs null numerator, zero denominator, OK) consumed by the rule harness (Task 13)
so semantic availability is explainable and versioned alongside the evaluator.

## Cross-product contract

Any product evaluating the same card version from the same certified snapshot must
produce the same value — asserted against the golden ratios in
`db/fixtures/expected/expected-covenants.csv`.
