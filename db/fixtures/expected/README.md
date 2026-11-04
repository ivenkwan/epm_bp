# Expected (Golden) Results — Hand Calculations

Every value below was computed **by hand from the fixture inputs**, documented step by step.
These files are the acceptance basis for the calculators built in later tasks; implementation
code must never generate or modify them. Money scale 2, ratios scale 6, FX rate scale 4;
rounding HALF_UP; division results truncated only at the final documented scale.

Reviewer sign-off status is recorded in `docs/operations/r0-build-status.md`.

## 1. Cash position — business date 2026-11-01 (rule `cash-1.0.0`, FX basis FX-V1)

Per account: `closing = opening + Σ movements` (movements carry signed amounts; direction is
descriptive only).

- ACC001 (USD, ENT101): opening `1000.00`; movements `+250.50 + (-100.25) = +150.25`;
  closing `1000.00 + 150.25 = 1150.25`
- ACC002 (EUR, ENT102): opening `500.00`; movement `-50.00`; closing `450.00`
- ACC003 (USD, ENT103): opening `250.00`; movement `+125.75`; closing `375.75`
- ACC004 (USD overdraft, ENT101): opening `-75.50`; movement `-4.50`; closing `-80.00`
  (negative balance on an authorized overdraft account is **valid**, not an error)

Group totals in USD at FX-V1 (EUR→USD 1.1000):
- Inflows USD: `250.50 + 125.75 = 376.25`
- Outflows USD: `100.25 + 4.50 = 104.75`
- USD closing (operating + overdraft): `1150.25 + 375.75 + (-80.00) = 1446.00`
- EUR closing `450.00` → USD `450.00 × 1.1000 = 495.00` (exact at scale 2, no rounding needed)
- Group closing: `1446.00 + 495.00 = 1941.00`
- Group opening: `1000.00 + 250.00 + (-75.50) + 550.00(EUR 500.00 × 1.1000) = 1724.50`
- Continuity check: `1724.50 + (376.25 − 104.75) + (−50.00 × 1.1000 = −55.00) = 1724.50 + 271.50 − 55.00 = 1941.00` ✔

## 2. Facility headroom (rule `treasury-1.0.0`)

`headroom = committed_limit − utilization`:

- FAC001: `1000000.00 − 625000.50 = 374999.50` → headroom positive
- FAC002: `500000.00 − 500000.00 = 0.00` → headroom exactly zero (boundary; not an anomaly)
- FAC003: `250000.00 − 250001.00 = −1.00` → over-limit anomaly: case must open, value kept explicit

## 3. Covenants (rule `cov-1.0.0`, ratios scale 6, HALF_UP)

- COV001 / ENT101: `850000.00 / 1700000.00 = 0.500000`; threshold `0.600000` inclusive ⇒
  headroom `0.600000 − 0.500000 = 0.100000`; status `COMPLIANT`
- COV001 / ENT103: denominator `0.00` ⇒ status `INVALID_NOT_COMPUTABLE` (never zero, never breach)
- COV002 / ENT102: `600000.00 / 500000.00 = 1.200000`; threshold `≥ 1.200000` inclusive ⇒
  headroom `0.000000`; status `COMPLIANT_AT_BOUNDARY` (exactly at inclusive threshold passes)

## 4. Intercompany matching (rule `ic-1.0.0`, tolerance 0.01 absolute on principal)

- ICL001 ↔ ICL002: principals equal (`200000.00`), currency equal, rate equal, tenor equal ⇒ `MATCHED`
- ICL003 ↔ ICL004: principal difference `80000.00 − 75000.00 = 5000.00 > 0.01` ⇒ `MISMATCHED_PRINCIPAL`
  with difference `5000.00` recorded, **no side overwritten, no automatic elimination**

## 5. Reconciliation (rule `recon-1.0.0`)

- TREC1 (adversarial): difference `1000.01 − 1000.00 = 0.01` equals tolerance `0.01` exactly ⇒
  `MATCHED_AT_EXACT_TOLERANCE` (inclusive boundary)
- A difference of `0.02` against tolerance `0.01` ⇒ `BLOCKED_OVER_TOLERANCE` (covered by unit
  tests; not a fixture row because tolerance files declare the boundary case only)

## 6. Planning allocation (rule `alloc-1.0.0`, largest-remainder at scale 2)

Driver basis HEADCOUNT, plan total `300.00` across ALC001=120, ALC002=80, ALC003=100 (Σ=300):

- Raw shares: `300.00 × 120/300 = 120.00`; `× 80/300 = 80.00`; `× 100/300 = 100.00` — exact,
  no residual; allocated total `300.00` ✔

Residual case (adversarial RES1-3): total `100.00`, three drivers of `33.33` each (Σ=99.99):

- Naive split `100.00 × 33.33/99.99 = 33.336667…` → rounded `33.34, 33.33, 33.33` by largest
  remainder (exact shares `33.336667`, `33.336667`, `33.336667`; floor `33.33` each leaves
  residual `0.01`; largest remainder is shared, order-first rule assigns to the first row by
  stable id) ⇒ final `33.34, 33.33, 33.33`, total `100.00` ✔ residual `0.01` assigned, none lost

## 7. Planning opening integration (P&L/BS/CF consistency, rule `plan-1.0.0`)

For BUDGET-2027 opening: BS assets `CASH_OPENING` and equity `RETAINED_EARNINGS_OPENING` are
declared per entity; the integrated check asserts
`ASSETS − (LIABILITIES + EQUITY) = 0.00` per entity — with liabilities declared as `0.00` in
this synthetic set:
- ENT101: `1250.00 − (0.00 + 5000.00) = −3750.00` ⇒ opening imbalance flagged (intentional:
  exercises the imbalance case playbook 8)
- ENT102: `450.00 − (0.00 + 2000.00) = −1550.00` ⇒ opening imbalance flagged

Both rows assert the *detector*, not balanced data: planning model imbalances must open cases,
never silently restate.

## 8. Integrated statements (rule `icstm-1.0.0`, scale 2, HALF_UP)

Inputs (`planning/integrated-statement-inputs.csv`, ENT-101 / 2027-P01): revenue.domestic
`1000.00`, revenue.export `500.00`, opcost.salaries `400.00`, opcost.other `100.00`, capex
`300.00`, depreciation `50.00`, opening.cash `2000.00`, opening.equity `2000.00`.

Hand calculations:

- revenue_total = 1000.00 + 500.00 = `1500.00`
- opex_total = 400.00 + 100.00 = `500.00`
- ebit = 1500.00 − 500.00 = `1000.00`
- tax = 1000.00 × 0.25 = `250.00` (only on positive ebit)
- net_income = 1000.00 − 250.00 = `750.00`
- cash_flow = 750.00 + 50.00 − 300.00 = `500.00`
- closing_cash = 2000.00 + 500.00 = `2500.00` (cash-to-cash-flow linkage)
- assets = 2500.00 + 300.00 − 50.00 = `2750.00`
- equity = 2000.00 + 750.00 = `2750.00`
- balance_difference = 2750.00 − 2750.00 = `0.00` ✔ exact integrity

Every row is reproduced by `IntegratedStatementService` (Task 42) — the calculator must
match these figures exactly, decimal-string end-to-end. Zero is a legitimate input
(`liabilities_total` is a true `0.00`); a null required input is an error, never zero.
