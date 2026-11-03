# DMN decision assets

Versioned decision tables with row- and boundary-level fixtures.

## Layout

- `kpi/ratio-availability.dmn` — KPI governed-unavailability table (Task 12).
- `validation/` and treasury tables land with their owning tasks (19, 23-26).

## Invariants

1. Every row has an independently expected result; hit policy is explicit.
2. Zero-denominator rows produce INVALID/unavailable outcomes — never a legal breach
   value; nulls never coerce to zero.
3. DMN evaluation is side-effect free; database and workflow transitions stay in
   Java/Kogito.
