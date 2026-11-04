# Driver and Scenario Model (Task 48)

Approved **drivers** (formulas, allowed ranges, dependencies, owners, worked examples,
versioned `drv-1.0.0`) power isolated **what-if scenarios**: immutable copies of a
locked baseline carrying volume/price/FX overrides.

## Governed semantics

1. **Isolation** — a scenario is a copy; the baseline and sibling scenarios are never
   mutated. Refused overrides leave the last valid results untouched.
2. **Ranges** — overrides outside a driver's approved range are refused with the
   range stated; unknown drivers are refused.
3. **Dependency safety** — cycles are detected and refused; evaluation depth is
   bounded by a declared cap.
4. **Determinism** — identical inputs replay identically (rule version
   `drv-1.0.0`, scale 2/6 HALF_UP); every result line carries a calculation trace.
5. **Comparison** — side-by-side diffs are exact and traceable to the override
   factors that caused them.
6. **Promotion** — a VALID scenario promotes as a **new governed version** of the
   plan (named approver required); never an in-place overwrite, and no automatic
   plan or actuals change exists.

Governed policy is also encoded in `rules/dmn/planning/drivers.dmn`
(`VALID` / `OUT_OF_RANGE` / `CIRCULAR`), kept in agreement with the service by test.

## Objects

| Object | Meaning |
|---|---|
| `epm_driver` | Approved drivers with ranges, formulas, versions, dependencies, owners |
| `epm_scenario` | Scenario header with baseline reference and status |
| `epm_scenario_override` | Volume/price/FX overrides inside approved ranges |
| `epm_scenario_result` | Result lines with calculation traces |
