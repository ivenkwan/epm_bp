# R3 Evidence Pack

Release: **R3 — EPM and Non-Driver Integrated Planning** (Tasks 39–46)
Branch: `r3-epm-planning` HEAD at rehearsal time; engineering evidence — pack and
statement acceptance, migration GO, and R4 authorization are the named owners'
recorded human decisions. Rehearsal date (UTC): 2026-11-04.

## Regression evidence

| Suite | Result |
|---|---|
| Maven reactor `./mvnw verify` (7 modules) | BUILD SUCCESS — 271 tests, 0 failures, 0 errors; 5 platform-smoke tests skipped in plain verify (cluster-gated; executed separately, 5/5) |
| finance-core | 61/61 (unchanged mandatory scope) |
| finance-integration | 28/28 |
| finance-planning (new R3 scope) | 52/52 — FX 11 + dimensions 3 + plan versions 10 + integrated statements 10 + calendar 7 + variance 10 + service 1 |
| finance-reporting | 45/45 — + management reports 15, dashboards 4, export formats 5 over the R2 suites |
| finance-admin | 32/32 |
| tests/integration | 47 tests: + PlanningCycleTest 4/4 (durable Kogito planning processes, no timer authority, restart) + PlanningMigrationTest 6/6 |
| tests/performance | 6/6 (R2 capacity/failure gates, still green) |
| finance-web | typecheck ✓, eslint ✓, vitest 15/15 ✓, build ✓ |
| e2e (Playwright + axe) | 24/24 — + management pack 4, recurring reporting 4, reporting/planning mobile 3 (200%-zoom reflow, keyboard reachability) |

## Quality gates

| Gate | Result |
|---|---|
| `verify-traceability.sh` | PASS — 212 requirements, zero unmapped |
| `verify-rules.sh` | PASS — 7 DRL/DMN assets (+ fx-translation, integrated-statements) |
| `verify-api-types.sh` / `verify-provenance.sh --quick` | PASS |
| `verify-migration.sh` | PASS — R0/R2 migration controls still green |
| `verify-r1-recovery.sh` | PASS — AGE rebuild, outbox replay, certification, fallback |
| `cutover-treasury.sh verify` | PASS |
| `run-planning-migration.sh --verify` | PASS — planning totals, cutoff, replay, independent reconciliation, fallback (Task 46) |
| Fixture manifest | 41 digests regenerated and verified (§8 integrated-statements set added) |

## Platform evidence

- Live kind cluster healthy (node Ready); `PlatformSmokeTest` 5/5 (anonymous 401,
  authenticated 200 + correlation, telemetry with governance labels, no token in
  exposition).
- Flyway V001–V032 applied (R3 adds V027–V032: management reports, planning
  dimensions/FX, plan versions, schedules/statements, planning calendar, KPI
  plans/variance); AGE lineage unchanged and green.
- Planning surfaces (finance-web): Planning tab (setup, versions, integrated model,
  calendar, variance) and Reporting tab (management packs, certified dashboards,
  recurring workspace); no browser-authoritative formula, rate, or lock anywhere.

## Recovery rehearsal

- Planning cutover/rollback/archive/manual-fallback/process-recovery rehearsed in
  `planning-cutover-and-fallback.md`; legacy fallback stays ACTIVE until an explicit
  GO (proven by `PlanningMigrationTest.fallbackStaysActiveUntilExplicitGo`).
- Process recovery: planning processes restart from definitions with
  Java/PostgreSQL-owned state (`PlanningCycleTest` restart cases); R2 restore/replay
  machinery (clean-container AGE restore, replay refused with exactly one effect)
  remains green.
- Migration replay: already-loaded planning records load zero new facts.

## Exclusions and boundaries

- R3 gate (packs + statements formally accepted; R4 authorized) remains a named-human
  decision — no business acceptance or go-live is claimed here.
- Legacy planning-tool retirement is a separate, later, explicitly approved decision;
  prior systems stay available until separately authorized.
- Signed expected results, confirmed-population performance, export fidelity
  inspection, and manual accessibility remain owner-gated inputs
  (`r3-parallel-run-report.md`, `r3-ux-acceptance.md`); no unmeasured number claimed.

## Open items

| ID | Item |
|---|---|
| R3-GATE | R3 GO/no-go — packs + integrated statements formally accepted; R4 explicitly authorized |
| R3-PAR | Parallel-run vs signed expected results (owner-prepared legacy outputs) |
| R3-PERF | Confirmed-population performance + signed workloads (pack/dashboard/drill/monthly template) |
| R3-UX | UX acceptance matrix: screen reader, device lab, export fidelity, cross-browser |
| O-B1/O-B7/O-B8 | Carried from R0–R2 (golden sign-off, manual accessibility, admin wiring) |
| OQ-006/OQ-007/OQ-010 | Open questions recorded at gate; R4 driver/scenario/allocation/elimination scope to be recorded under OQ-007 |

## Sign-offs (owners)

- [ ] FP&A sign-off — shared dimensions, FX basis, integrated statements, lock/reopen policy, variance/KPI results
- [ ] EPM sign-off — management packs accepted
- [ ] Business feature sign-off — representative packs and journeys
- [ ] Finance control sign-off — Must controls intact
- [ ] Security sign-off — security/privacy evidence current
- [ ] Operations sign-off — cutover/fallback/handover accepted
- [ ] Migration sign-off — parallel run + fallback evidence
- [ ] Accessibility sign-off — no critical barrier remains
- [ ] Technical sign-off — capacity + performance evidence
