# Facility Maintenance Runbook

## Governed terms

Facility terms (committed limits, utilization, currencies, rates, maturities, approved
over-limit classifications) are effective-dated governed master data: every change is a
new version through the steward journey (Task 9 discipline) with actor, reason, and
old/new evidence. The platform never changes terms autonomously.

## Headroom (rule `treasury-1.0.0`)

```
headroom = committed_limit − utilization        (scale 2, HALF_UP, decimal strings)
```

- **Exact zero** (`AT_LIMIT_EXACT_ZERO`) is a boundary value, not an anomaly.
- **Over-limit** (`OVER_LIMIT_ANOMALY`) keeps the negative value explicit, surfaces as
  an owned exception, and never silently clamps to zero or edits the facility terms.
- Anomalies open an owned exception case (Task 14) with the facility, limit,
  utilization, headroom, and term version; resolution is a human decision.

## Deposits

Deposit schedules reconcile to the certified cash position: Σ deposits must tie to the
certified deposit-carrying amount exactly (scale 2). Maturity on the position date
follows the effective-dated schedule (a deposit maturing on the position date is a
governed classification recorded in the terms, not an operator choice).

## Unauthorized edits

There is no API that edits facility terms from a calculated view; edits flow only
through the governed master-data change path with independent approval. Any attempt to
change terms from a funding view is denied (Task 8 enforcement) and audited.
