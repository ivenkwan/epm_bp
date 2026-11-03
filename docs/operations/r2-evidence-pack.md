# R2 Evidence Pack

Release: **R2 — Daily Treasury Scale and Cutover** (Tasks 35–38)
Branch: `r2-scale` HEAD at rehearsal time; engineering evidence — cutover authorization
is the named owners' recorded human decision (`r2-go-no-go-decision.md`).
Rehearsal date (UTC): 2026-11-03.

## Regression evidence

| Suite | Result |
|---|---|
| Maven reactor `./mvnw verify` (7 modules) | BUILD SUCCESS — 186 tests, 0 failures, 0 errors; 5 platform-smoke tests skipped in plain verify (cluster-gated; executed separately, 5/5) |
| finance-core | 61/61 — + stock/unit context 5 (F-022 optional scope) over the R1 suites |
| finance-integration | 28/28 |
| finance-reporting | 21/21 |
| finance-admin | 32/32 |
| tests/integration | 37 tests: + TreasuryRecoveryTest 2/2 (restore into clean AGE container, replay refused) |
| tests/performance (new module) | 6/6 — capacity/failure gates: 8,000 concurrent submissions exact; duplicate/replayed feeds identical; certification contention 1-win/23-fail-closed; 20k backlog lossless; watermark catch-up exact; late/missing governed |
| finance-web | typecheck ✓, eslint ✓, vitest 15/15 ✓, build ✓ |
| e2e (Playwright + axe) | 13/13 — + stock/unit context 2 (disabled default visible; no leakage) |

## Quality gates

| Gate | Result |
|---|---|
| `verify-traceability.sh` | PASS — 212 requirements, zero unmapped |
| `verify-rules.sh` | PASS — 5 DRL/DMN assets |
| `verify-api-types.sh` / `verify-provenance.sh --quick` | PASS |
| `verify-migration.sh` | PASS — totals, tolerance, cutoff, replay, fallback |
| `verify-r1-recovery.sh` | PASS — AGE rebuild, outbox replay, certification, fallback |
| `cutover-treasury.sh verify` | PASS — migration + recovery gates |
| `verify-restore.sh` (contract) | Proven by `TreasuryRecoveryTest` against containers; executes against a live target when `EPM_PG_*` is set |

## Recovery rehearsal

- `TreasuryRecoveryTest` 2/2: restore into a clean AGE container; post-restore replay refused with exactly one surviving effect.
- Cutover phases rehearsed green (freeze / verify / cutover human gate / rollback).
- Production-shaped restore rehearsal + measured RPO/RTO: owner-gated handover item.
| Fixture manifest | 39 digests regenerated and verified |

## Platform evidence

- Live kind cluster healthy; `PlatformSmokeTest` 5/5 (anonymous 401, authenticated
  200 + correlation, telemetry with governance labels, no token in exposition).
- Flyway V001–V026 applied; AGE lineage rebuild + watermark gate green.
- Autoscaling configured (API on load 2–6 @65% CPU; workers on queue age) with
  financial semantics unchanged (service/DB-enforced safety).

## Wave onboarding (Task 35)

- Register delivered (`r2-onboarding-register.md`): per-wave owner, contract,
  template/feed, grain, cutoff, control totals, historical depth, entitlement scope,
  readiness evidence, rollback boundary; unregistered "complete" claims rejected by
  `onboard-treasury-scope.sh` (fail-closed, runs the five check families per wave).
- Capacity/failure gates delivered **before scaling** (`PeakBacklogTest` 6/6); the
  signed 2×-peak / return-to-normal workload executes against the confirmed population
  and its p50/p95/p99 + resource evidence attaches here (owner-gated R2 measurement —
  **no unmeasured number claimed**).
- Autoscaling configuration delivered (API on load 2–6 replicas @65% CPU; workers on
  queue age 30s) with unchanged financial semantics (safety is service/DB-enforced).

## Optional scope (Task 36)

F-022 stock/unit liquidity context: **signed deferral recorded** by default
(`r2-optional-scope-decision.md`); feature disabled fail-closed (service throws,
table inert, browser shows the disabled state — e2e 2/2); zero impact on mandatory
scope. Activation executes the delivered enabled-mode suite (5/5) the moment an owner
signs.

## Recovery and cutover (Task 37)

- Backup/restore/verify scripts delivered with immutable evidence (manifests + SHA-256
  digests; credentials from env only); AGE installed consistently on every target.
- `TreasuryRecoveryTest` 2/2: restore into a clean AGE container verifies AGE +
  schema + zero duplicate certifications; post-restore replay refused with exactly one
  surviving effect.
- Cutover phases rehearsed green (freeze with recorded UTC, verify running both
  operational gates, cutover naming the human go/no-go, rollback preserving the
  divergence window); corrections-only discipline is structural (F-035 ledger, no
  direct-edit path).
- Operational-readiness record maps every handover item to evidence and names the
  human checklist (training, severity exercise, support access, witnessed restore).

## Exclusions and boundaries

- Cutover remains blocked until named humans sign `r2-go-no-go-decision.md`; R3 starts
  only after treasury stability is accepted. No business go-live claimed here.
- Legacy retirement is a separate, later, explicitly approved decision — technical
  go-live is never retirement approval (retirement register delivered with Task 38).

## Open items

| ID | Item |
|---|---|
| R2-M1 | Confirmed-population workload: 2× peak, return-to-normal, parallel-run evidence |
| O-B1/O-B7/O-B8 | Carried from R0/R1 (golden sign-off, manual accessibility, admin wiring) |
| OQ-004/005 | Partial-publication rules; forecast parameters (safe defaults active) |
| Handover | Training, severity exercise, support access, witnessed production restore |

## Go/no-go sign-offs (owners)

- [ ] Business — population accepted
- [ ] Treasury — outputs reconciled or signed exceptions
- [ ] Migration — parallel run + fallback evidence
- [ ] Control — Must controls intact; exceptions dispositioned
- [ ] Security — security evidence current
- [ ] Technical — capacity + performance evidence
- [ ] Operations — handover accepted
