# Runbook: Planning Cutover and Fallback (Task 46)

Controlled cutover of planning and management reporting from legacy tools to the
platform. Technical migration is never retirement approval: prior systems stay
available until a separate, explicitly authorized retirement decision.

## Preconditions (fail-closed)

1. Every planning object class (PLAN_VERSION, PLANNING_DIMENSION, FX_BASIS,
   INPUT_SCHEDULE, PLAN_COMPARISON) is REGISTERED in `epm_migration_register` with
   control totals, cutoff rule, replay identity, and archive reference — verified by
   `run-planning-migration.sh --verify` (`PlanningMigrationTest`).
2. Integrated statements reconcile independently against migrated control totals.
3. Parallel-run evidence covers packs, statements, tasks, approvals, locks,
   variances, exports, and performance against signed expected results
   (`r3-parallel-run-report.md`).
4. The named owners' R3 gate decision is recorded (see the evidence pack).

## Cutover phases

| Phase | Act | Guard |
|---|---|---|
| Freeze | Record UTC; stop new legacy planning submissions | freeze window logged |
| Verify | Run `verify-release.sh --release R3` + planning migration verify + recovery gates | any failure aborts |
| Cutover | Named humans record GO in the R3 gate; planning cycles open on the platform | GO is a human act |
| Monitor | Parallel/monitoring interval with reconciliation evidence | interval agreed by owners |

## Rollback

- The legacy fallback stays ACTIVE through cutover and the monitoring interval
  (`MigrationRunner.fallbackActive`); divergence-window work is preserved, not
  overwritten.
- Rollback re-opens the legacy path for in-flight cycles; already-locked platform
  plan versions remain immutable evidence — never edited or deleted to force a
  rollback.

## Manual fallback

- If the platform is unavailable mid-cycle: preparers record inputs on the governed
  fallback templates (the same source contracts), and the platform ingests them
  through the normal landing→validation→reconciliation chain on return. No
  out-of-band spreadsheet becomes authoritative.

## Process recovery

- Planning processes (planning-cycle, plan-version-approval BPMN) restart from
  definitions; all business state is Java/PostgreSQL-owned (`epm_plan_version`,
  `epm_planning_cycle/task/decision`) — proven by `PlanningCycleTest` restart cases
  and the R2 recovery rehearsal machinery (AGE-first restore, replay refusal).

## Archive and retirement

- Legacy planning artifacts archive to the referenced archive store with digests.
- Retirement of any legacy planning system is a separate, explicitly approved human
  decision recorded in `legacy-retirement-register.md` — after the stability
  interval, never as a consequence of technical go-live.

## Records

| Record | Where |
|---|---|
| Migration register rows | `epm_migration_register` |
| Control totals + replay identity | `PlanningMigrationTest`, migration register |
| Parallel-run evidence | `docs/operations/r3-parallel-run-report.md` |
| Cutover GO decision | R3 evidence pack sign-offs |
| Retirement decisions | `docs/operations/legacy-retirement-register.md` |
