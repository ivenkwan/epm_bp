# Cash Forecast Model

## Immutable forecast versions (rule `forecast-1.0.0`)

A forecast version is an immutable record: version id, seed date, opening cash, and the
direct receipt/payment schedule per future period (decimal strings, scale 2). Edits
create a **new version**; the last approved version is never mutated.

## Roll-forward (exactly once)

At each cadence point the next period rolls forward **exactly once**:

```
closing = opening + receipts − payments        (scale 2, HALF_UP)
```

- Opening cash must equal the **prior certified close** (Task 23). A non-reconciling
  opening is a governed rejection showing both values — never an assumption, never a
  silent re-seed.
- The roll consumes exactly one schedule line; rolling again before the next cadence
  produces nothing new (the approved baseline is preserved).
- Before the period is due, the approved version stays untouched.

## Variance

Actual-versus-forecast compares the certified actual closing to the forecast closing per
period (scale 2, signed difference), with the comparison basis (version ids) carried on
the versioning record.

## Assumptions (OQ-005)

Horizon, cadence, period count, seed rules, and the actual-comparison basis come from
signed OQ-005 decisions recorded before acceptance fixtures are frozen; until then the
calculator implements the general contract above with no hidden assumptions.
