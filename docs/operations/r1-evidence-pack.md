# R1 Evidence Pack

Release: **R1 — Daily Treasury Reporting Lighthouse**
Branch: `r1-lighthouse` HEAD at rehearsal time (engineering evidence; the R1 gate
decision requires the sign-offs below).
Rehearsal date (UTC): 2026-11-03.

## Regression evidence

| Suite | Result |
|---|---|
| Maven reactor `./mvnw verify` (6 modules) | BUILD SUCCESS — 157 tests, 0 failures, 0 errors; 5 platform-smoke tests skipped in plain verify (cluster-gated; executed separately, 5/5) |
| finance-core | 56/56 — outbox concurrency 7, API-safety 6, certification control 6, daily cutoff 6, cash position 6, funding headroom 4, intercompany 5, covenant boundary 5, rolling forecast 5, adjustment control 5, smoke 1 |
| finance-integration | 28/28 — source contracts 8, landing immutability 4, scheduled ingestion 5, template submissions 5, validation boundaries 4, durable jobs 1, smoke 1 |
| finance-reporting | 17/17 — KPI evaluation 7, publication 6, AGE rebuild 3, smoke 1 |
| finance-admin | 25/25 — authorization 8, case recovery 10, hierarchy 6, smoke 1 |
| tests/integration | 30 tests: toolchain 5/5, rule governance 6/6, fixture integrity 9/9, design contracts 5/5, platform smoke 5/5 (with `EPM_PLATFORM_SMOKE=true`) |
| finance-web | typecheck ✓, eslint ✓, vitest 15/15 ✓, production build ✓ |
| e2e (Playwright + axe) | 7/7 — accessibility 4 (skip link, keyboard, 200% reflow, WCAG zero critical/serious) + R0 control journeys 3 |

## Golden-calculator fidelity (the §-by-§ basis)

| Calculator | Golden source | Result |
|---|---|---|
| Cash position `cash-1.0.0` | `expected-cash-position.csv` + §1 (group 1941.00) | Every account row exact; FX-V1 rollup exact; continuity 0.00/5.50; overdraft −80.00 explicit; float-trap exact |
| Headroom `treasury-1.0.0` | `expected-headroom.csv` §2 | 374999.50 / 0.00 boundary / −1.00 anomaly exact |
| Covenants `cov-1.0.0` | `expected-covenants.csv` §3 | 0.500000, 1.200000 inclusive boundary, INVALID zero-denominator exact |
| Intercompany `ic-1.0.0` | `expected-intercompany-matching.csv` §4 | MATCHED 0.00 / MISMATCHED 5000.00 exact |
| Adjustments (Task 15 contract) | `adjustment-example.csv` §5 + design tests | Balance, materiality boundaries, reversal net-zero exact |
| Reconciliation | `expected-reconciliation.csv` + TREC1 | Exact-tolerance inclusive; over-tolerance blocks |

All values reproduce the independently hand-calculated expectations in
`db/fixtures/expected/README.md`; no calculator reads its golden as input.

## Quality gates

| Gate | Result |
|---|---|
| `verify-traceability.sh` | PASS — 212 requirements traced, zero unmapped |
| `verify-rules.sh` | PASS — 4 DRL/DMN assets (validation DRL, KPI availability DMN, covenant DMN, adjustment-level DMN), XML well-formed, governance suite 6/6; the covenant and adjustment tables are additionally row-exercised by their owning suites |
| `verify-api-types.sh` | PASS |
| `verify-provenance.sh --quick` | PASS |
| Fixture manifest | 39 digests regenerated and verified |

## Platform evidence

- Live kind cluster (Task 4 deployment, updated finance-core image with V001–V022
  migrations): all pods Running; `PlatformSmokeTest` 5/5 (anonymous 401, authenticated
  200 + correlation, spans + metric points accepted, governance labels, no token in
  telemetry).
- AGE lineage: rebuild + equivalence green (`AgeRebuildTest`), watermark freshness gate
  blocks stale dependent certification.

## Exclusions and boundaries

- No business go-live claim: R1 delivers the engineering lighthouse; parallel-run and
  cutover decisions are the named owners' (migration runbooks, Task 15).
- F-043/F-044 exclusions remain route-absent (live 404 probes re-verified during the
  platform smoke in R0; contract tests unchanged and green).

## Open items (R1-specific)

| Item | Status |
|---|---|
| Representative-cohort parallel run + latency/throughput measurements (≤60-min certification target) | PENDING — requires the named cohort and production-shaped workload (owners) |
| OQ-004 partial-publication rules; OQ-005 forecast parameters | PENDING — named-owner decisions; defaults are the safe ones (hold; documented general contract) |
| O-B1 golden sign-off (Finance/Treasury) | PENDING — every calculator now reproduces the goldens; the sign-off is the human control |
| O-B8 admin browser wiring (operator/steward/case views) | PENDING — all service-side journeys and evidence exist |
| O-B6 (durable-job runtime) | RESOLVED this release — `JobService` landed in finance-integration (replay returns the same job; exactly-once claim) |

## Sign-offs

- [ ] Product owner — R1 scope accepted
- [ ] Treasury control owner — golden fidelity, covenant formulas, cutoff semantics
- [ ] Security owner — authorization surface unchanged and green
- [ ] Operations owner — platform, runbooks, recovery evidence
- [ ] Assurance owner — evidence completeness and traceability

R2 (scale) is authorized only when the sign-offs above are recorded and the parallel-
run measurement is complete with a scale/remediate/stop decision.
