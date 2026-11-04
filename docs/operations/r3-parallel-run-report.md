# R3 Parallel-Run Report

Parallel management-reporting and planning cycles comparing platform outputs against
expected results. Engineering-executed comparisons are recorded here with evidence;
comparison against **signed** expected results (owner-prepared legacy outputs) is an
owner-gated input recorded in the sign-off block — no unverified agreement claimed.

## Scope compared

| Dimension | Platform capability | Automated comparison evidence |
|---|---|---|
| Management packs | Task 39 governed packs, immutable editions, reproduction hash | `ManagementReportServiceTest` 15/15 (shared-figure equivalence, scope certification, reproduction) + e2e 4/4 |
| Integrated statements | Task 42 icstm-1.0.0 linked P&L/BS/CF | `IntegratedStatementTest` 10/10 — golden §8 exact reproduction, replay + concurrent identity, balance exactness |
| Planning tasks/approvals/locks | Tasks 41/43 versions, calendar, level approvals | `PlanVersionTest` 10/10, `PlanningCalendarTest` 7/7, `PlanningCycleTest` 4/4 (no timer authority) |
| Variances | Task 44 certified-catalog variance | `VarianceCalculationTest` 10/10 — golden arithmetic, drill reconciliation exact |
| Exports | Task 45 governed Excel/PDF/presentation | `ExportFormatServiceTest` 5/5 + e2e recurring-reporting 4/4 |
| Migration | Task 46 planning object classes | `PlanningMigrationTest` 6/6 — control totals, cutoff, replay refusal, independent reconciliation |

## Cycle evidence (engineering runs)

- Full planning cycle replay: prepare → submit → level 1 → level 2 → final lock
  executes on the durable Kogito runtime with named human tasks only; rejection and
  resubmission preserve chronology (`PlanningCalendarTest`).
- Integrated statement reconciliation is **independent**: `PlanningMigrationTest`
  re-derives the §8 figures with hand-coded arithmetic, not the planning service.
- Pack reproduction: regenerating a pack from identical inputs reproduces the same
  content hash (`ManagementReportServiceTest`).

## Owner-gated inputs (blocking acceptance)

| # | Input | Owner |
|---|---|---|
| 1 | Signed expected packs/statements prepared from legacy tools for the representative cycle | FP&A + EPM |
| 2 | Parallel-run interval results vs signed expected results | Business owners |
| 3 | Performance measurements at the confirmed population (p50/p95/p99) | Technical |
| 4 | Export fidelity inspection (actual Excel/PDF/presentation outputs) | Business owner |
| 5 | Accessibility manual pass (screen reader + device lab) | Accessibility owner |

## Sign-off

- [ ] FP&A — statements and planning cycles reconcile to signed expected results
- [ ] EPM — management packs accepted
- [ ] Business — export fidelity and parallel-run acceptance
- [ ] Control — Must controls intact
