# R4 Evidence Pack

Release: **R4 — Advanced Planning, Reporting, and Selective AI** (Tasks 47–57)
Branch: `r4-advanced-planning` HEAD at rehearsal time; engineering evidence — final
product acceptance, AI enablement, and production handover are the named owners'
recorded human decisions (`final-acceptance.md`). Rehearsal date (UTC): 2026-11-04.

## Regression evidence

| Suite | Result |
|---|---|
| Maven reactor `./mvnw verify` (7 modules) | BUILD SUCCESS — 335 tests, 0 failures, 0 errors; 5 platform-smoke tests skipped in plain verify (cluster-gated; executed separately, 5/5) |
| finance-core | 61/61 (unchanged mandatory scope) |
| finance-integration | 28/28 |
| finance-planning | 66/66 — + scenarios 8 + allocations 6 over the R3 suites (eliminations tested in integration) |
| finance-reporting | 74/74 — + self-analysis 5, AI grounding/security 9, commentary pilot 4, KPI Q&A 6, challenger 5 |
| finance-admin | 38/38 — + mapping studio 6 |
| tests/integration | 59 tests — + ManagementEliminationTest 4, AdvancedPromotionTest 5, FinalAdversarialTest 3 |
| tests/performance | 9/9 — + DashboardDrillLoadTest 1, FinalAcceptanceWorkloadTest 2 |
| finance-web | typecheck ✓, eslint ✓, vitest 15/15 ✓, build ✓ |
| e2e (Playwright + axe) | 32/32 — + full self-service 4, mapping studio 4 |

## Quality gates

| Gate | Result |
|---|---|
| `verify-traceability.sh` | PASS — 216 requirements, zero unmapped (incl. F-045/AC-F-045-01–03) |
| `verify-rules.sh` | PASS — 10 DRL/DMN assets (+ drivers, management-eliminations) |
| `verify-api-types.sh` / `verify-provenance.sh --quick` | PASS — provenance now includes the living-documentation check (every governed rule/process asset doc-referenced) |
| `verify-security.sh --suite ai` | PASS — control-plane negatives, registry ships disabled, no credential literals |
| `verify-migration.sh` / `verify-r1-recovery.sh` / `cutover-treasury.sh verify` | PASS (R0–R2 gates still green) |
| `run-planning-migration.sh --verify` | PASS |
| `verify-final-recovery.sh` | PASS — R1 recovery + cutover + planning migration + AI security + adversarial + final workloads in one gate |
| Fixture manifest | 41 digests verified |

## Platform evidence

- Live kind cluster healthy; `PlatformSmokeTest` 5/5 (anonymous 401, authenticated
  200 + correlation, telemetry governance labels, no token exposition).
- Flyway V001–V037 applied (R4 adds V033–V037: drivers/scenarios, allocations,
  eliminations, AI snapshots/provenance, mapping studio).
- Browser surfaces: Planning tab extended (scenarios, allocations, eliminations,
  mapping studio, AI panels); Reporting tab extended (self-service actions); all
  panels reflow at 200% zoom and stay keyboard-operable.

## Recovery rehearsal

- `verify-final-recovery.sh` exercises every recovery gate end to end: AGE rebuild,
  outbox replay, certification restore, treasury cutover phases, planning migration
  fallback-active-until-GO, AI kill switch, and the adversarial denial suite.
- Final workloads: 625-entity integrated model balances exactly (5,000 lines);
  20,000-row drill pages under the governed cap and reconciles to its KPI total.
- Incident command runbook delivered (`production-incident-command.md`) with
  severity classes and first moves.

## AI governance (Tasks 51–54)

- All three routes (`commentary-draft`, `kpi-qa`, `forecast-challenger`) ship
  **disabled** with signed enablement gates in their evaluation records.
- Control-plane negatives proven: unapproved/uncertified/kill-switched refusals,
  numeric-fidelity suppression, injection invalidation, cross-entity filtering on
  both passes, telemetry redaction, env-only credentials.
- Deterministic fallbacks are complete and proven alongside disabled routes;
  nothing in reporting, planning, or commentary depends on a generative route.

## Exclusions and boundaries

- F-043/F-044: statutory-representation and autonomous-execution denials proven by
  `FinalAdversarialTest` (maps `tests/security/final/`); no statutory capability and
  no autonomous payment/posting/approval path exists.
- Final product acceptance, AI enablement, signed performance runs, UX manual
  passes, and handover remain owner-gated (`final-acceptance.md`); no adoption
  target is claimed as achieved (`production-adoption-report.md`).
- Legacy retirement remains a separate, later, explicitly approved decision.

## Open items

| ID | Item |
|---|---|
| R4-GATE | Final product acceptance + operational handover (12 signatures) |
| AI-ENABLE | Tier 1/2 route enablement (AI/Security/Domain + timed drills) |
| R4-PERF | Signed 2×-peak/population performance runs |
| R4-UX | UX matrix manual passes (screen reader, device lab, export fidelity, cross-browser) |
| OQ-007/008/010/011/012 | Open-question dispositions recorded at the gate |
| R2/R3 carried | Prior gates' owner inputs remain open until their owners sign |

## Final sign-offs (owners)

- [ ] Product — journeys and adoption accepted
- [ ] Executive/Assurance — governance and evidence accepted
- [ ] Group Finance/EPM — management reporting accepted
- [ ] Treasury — treasury scope accepted
- [ ] FP&A — planning and integrated statements accepted
- [ ] Divisional/Entity — representative journeys accepted
- [ ] Security — security/privacy/AI governance accepted
- [ ] Operations — handover and incident readiness accepted
- [ ] Migration — migration/fallback evidence accepted
- [ ] Accessibility — no critical barrier remains
- [ ] Technical — capacity and performance evidence accepted
- [ ] Control — Must controls intact
