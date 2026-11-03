# R1 Evidence Pack

Release: **R1 — Daily Treasury Reporting Lighthouse** (Tasks 17–34)
Branch: `r1-lighthouse` HEAD at rehearsal time; engineering evidence — the G1 decision
and sign-offs below are the named owners' human controls.
Rehearsal date (UTC): 2026-11-03.

## Regression evidence

| Suite | Result |
|---|---|
| Maven reactor `./mvnw verify` (6 modules) | BUILD SUCCESS — 173 tests, 0 failures, 0 errors; 5 platform-smoke tests skipped in plain verify (cluster-gated; executed separately, 5/5) |
| finance-core | 56/56 — outbox 7, API-safety 6, certification 6, daily cutoff 6, cash position 6, headroom 4, intercompany 5, covenants 5, forecast 5, adjustments 5, smoke 1 |
| finance-integration | 28/28 — contracts 8, landing 4, ingestion 5, templates 5, validation 4, durable jobs 1, smoke 1 |
| finance-reporting | 21/21 — KPI 7, publication 6, exports 4, AGE rebuild 3, smoke 1 |
| finance-admin | 32/32 — authorization 8, case recovery 10, hierarchy 6, break-glass 7, smoke 1 |
| tests/integration | 35 tests: toolchain 5/5, rule governance 6/6, fixture integrity 9/9, design contracts 5/5, migration replay 5/5, platform smoke 5/5 |
| finance-web | typecheck ✓, eslint ✓, vitest 15/15 ✓, build ✓ |
| e2e (Playwright + axe) | 11/11 — accessibility 4, R0 control journeys 3, integrated treasury journeys 4 |

## Golden-calculator fidelity (§-by-§ against `db/fixtures/expected/`)

| Calculator | Golden | Result |
|---|---|---|
| Cash position `cash-1.0.0` | §1 + `expected-cash-position*.csv` | every account row exact; group 1941.00 at FX-V1; continuity 0.00/5.50; overdraft −80.00 explicit; float-trap exact |
| Headroom `treasury-1.0.0` | §2 | 374999.50 / 0.00 boundary / −1.00 anomaly exact |
| Covenants `cov-1.0.0` | §3 | 0.500000; inclusive 1.200000; zero-denominator INVALID (null ratio) exact |
| Intercompany `ic-1.0.0` | §4 | MATCHED 0.00 / MISMATCHED 5000.00 exact |
| Reconciliation | TREC1 | exact-tolerance inclusive; over-tolerance blocks |
| Adjustments (Task 15 contract) | §5 | balance, materiality boundaries, reversal net-zero exact |
| Migration (Task 15 contract) | migration fixtures | control totals, tolerance blocker, T0 cutoff, replay zero-facts exact |

## Quality gates (all green at rehearsal)

| Gate | Result |
|---|---|
| `verify-traceability.sh` | PASS — 212 requirements, zero unmapped |
| `verify-rules.sh` | PASS — 5 DRL/DMN assets (validation DRL; KPI, covenant, adjustment DMN; + treasury DMN exercised per-suite) |
| `verify-api-types.sh` | PASS |
| `verify-provenance.sh --quick` | PASS |
| `verify-migration.sh` | PASS — totals, tolerance, cutoff, replay, fallback |
| Fixture manifest | 39 digests regenerated and verified |
| CycloneDX SBOM | generated per build; CI publishes |

## Platform evidence

- Live kind cluster: all pods Running; Flyway V001–V025 applied; `PlatformSmokeTest`
  5/5 (anonymous 401, authenticated 200 + correlation, telemetry received with
  governance labels, no token in exposition).

## Recovery rehearsal

- `verify-r1-recovery.sh` green: AGE rebuild + watermark gate, outbox replay,
  certification recovery, migration fallback discipline.
- R0 drills re-validated on the live cluster: restart drill, PostgreSQL schema
  restore (33 tables), AGE graph durable on PVC.
- Treasury manual-fallback runbook delivered; the migration fallback stays active
  until an explicit GO.

## Human-intervention scenarios covered by tests

1. Validation errors (T19) 2. Reconciliation breaks (T20) 3. Late/missing data (T22)
4. Mapping/KPI changes (T9/T12) 5. Manual adjustments (T29) 6. Approval rejection
and resubmission (T18/T28) 7. Intercompany mismatches (T25) 8. Planning imbalances
(T15 design contracts) 9. Covenant alerts (T26) 10. AI output (out of R1 scope) 11.
Production/model incidents (T30 break-glass) 12. Break-glass access (T30).

## Performance measurement (R1-M1 — PENDING, no unmeasured target claimed)

Template validation, certified-position latency, dashboard/drill, and backlog-recovery
workloads run against the signed representative cohort; proposed targets (p95 ≤ 30 s,
≤ 60 min, ≤ 3 s, ≤ 5 s) remain proposals until measured. The parallel-run protocol and
machinery are recorded in `docs/operations/r1-parallel-run-report.md`.

## Security evidence

- Authorization: 8/8 (restricted-entity no-leak, empty scope, self-approval, SoD,
  overdue review, gateway bypass); break-glass 7/7 (dual approval, denied financial
  actions, no standing privilege); delegation + break-glass schema CHECKs.
- Exports: 4/4 (recipient re-authorization, no silent truncation, retention logs).
- SAST/SCA/secrets: CI runs dependency review + gitleaks; CycloneDX SBOM per build;
  unresolved critical/high = none recorded by the automated gates.
- Telemetry redaction: platform smoke asserts token absence + attribute redaction.

## Exclusions and boundaries

- No business go-live claim and no automatic transition to R2: the G1 decision is the
  named owners' recorded human act (`docs/operations/g1-scale-decision.md`).
- F-043/F-044 excluded behavior remains route-absent (contract negatives green); the
  deterministic path works with AI disabled (no AI is wired at R1 by design).
- Management-only eliminations / statutory consolidation remain absent (R3+/excluded).
- Performance targets are proposals until R1-M1 measures them; nothing in this pack
  claims an unmeasured number.

## Open items

| ID | Item |
|---|---|
| R1-M1 | Cohort parallel run + performance measurements (owners' workload) |
| O-B1 | Finance/Treasury golden + control-design sign-off |
| O-B7 | Manual accessibility verification (screen reader/contrast) |
| O-B8 | Admin browser wiring (operator/steward/case/publication screens) |
| OQ-004/005 | Partial-publication rules; forecast parameters (safe defaults active) |

## G1 decision (recorded by owners; no automatic transition to R2)

- [ ] **scale** — all mandatory evidence and measurements pass
- [ ] **remediate** — correctable gaps returned to named R1 tasks
- [ ] **stop** — financial integrity, security, or human authority cannot be assured

Sign-offs: Product / Finance & Treasury / Technical / Security / Operations /
Migration / Control.
