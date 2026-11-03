# Covenant Alert Runbook

## Formula cards (versioned)

Every covenant has a signed formula card before monitoring runs: stable id, formula
identifier (`gearing` style ratios), numerator/denominator source mappings, threshold
with **inclusive** comparison, scenario basis (actual/forecast), materiality, owner,
cadence, and worked examples (Task 12 KPI-card discipline). Cards are versioned; an
alert always cites the frozen formula version and source snapshot.

## Evaluation (rule `cov-1.0.0` + DMN `rules/dmn/treasury/covenants.dmn`)

```
ratio    = numerator / denominator        (scale 6, HALF_UP, decimal strings)
headroom = threshold − ratio              (scale 6, HALF_UP)
status   = COMPLIANT when headroom ≥ 0 (threshold inclusive)
```

- **Exact boundary**: a ratio exactly at the threshold is COMPLIANT with headroom
  `0.000000` (golden COV002: 1.200000 vs 1.200000).
- **Zero denominator**: `INVALID_NOT_COMPUTABLE` — the ratio stays null (never
  `0.000000`) and no breach claim is made.
- **Null numerator**: quarantined upstream by validation (NULL_AMOUNT); the calculator
  never receives one — a crash on null is the contract, not a coercion to zero.

## Alerts and human authority

An alert opens an exception case (Task 14, playbook 9) with the covenant, entity,
ratio, threshold, headroom, formula version, and source revisions. **Named human
Treasury authority** records the interpretation and any external communication
(lender notification, waiver discussion); the platform takes no autonomous legal or
financial action and substitutes no parameters.

## Stale or missing inputs

Stale inputs open a case and block the affected certification (Task 20 gate via the
Task 21 freshness watermark); monitoring does not proceed on assumed data.
