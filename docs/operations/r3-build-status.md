# R3 Build Status

Per-task results for the R3 EPM-and-integrated-planning release (Tasks 39–46). Human
decisions (FP&A/EPM sign-offs, UX acceptance, migration GO) live in
`docs/operations/r3-evidence-pack.md` and `r3-parallel-run-report.md`.

| Task | Scope | Result | Evidence |
|---|---|---|---|
| 39 | Enterprise/division management reports + initial decision packs | PASS (pack workload owner-gated) | `V027`; `ManagementReportServiceTest` 15/15; `ManagementReportsPanel` + e2e `management-pack.spec.ts` 4/4; `management-report-model.md`; no report-local KPI calculation by construction |
| 40 | Shared planning dimensions + multi-currency FX rules | PASS (FP&A worked-example sign-off open) | `V028`; `FxTranslationTest` 11/11 + `PlanningDimensionTest` 3/3; `fx-translation.dmn` policy table; zero stays zero, null never coerced, no adjacent-period inference, no unapproved substitution; `PlanningSetupPanel` |
| 41 | Plan versions, seeding, locks, controlled reopening | PASS (FP&A lock/reopen sign-off open) | `V029`; `PlanVersionTest` 10/10 (seed-once, no-data outcome, stale-ETag, restart, reopen lineage); `plan-version-approval.bpmn` + `PlanVersionsPanel`; `plan-version-reopening.md` |
| 42 | Non-driver schedules + integrated P&L/BS/CF | PASS (FP&A acceptance open) | `V030`; `IntegratedStatementTest` 10/10 incl. golden §8 exact match, linked recalculation, imbalance case playbook 8, replay/concurrent identity; fixtures + manifest regen; `integrated-planning-model.md` |
| 43 | Planning calendar, multi-level approvals, reminders | PASS (representative signed cycle open) | `V031`; `PlanningCalendarTest` 7/7 + `PlanningCycleTest` 4/4 (Kogito named-human sequencing, no timer authority, restart); `planning-cycle.bpmn`; `planning-cycle.md`; 5-business-day measurement reported not claimed |
| 44 | Actuals variance + shared KPI planning | PASS (FP&A/EPM sign-off open) | `V032`; `VarianceCalculationTest` 10/10 (zero-baseline undefined, uncertified exclusion, exact drill reconciliation); `variance-and-kpi-planning.md`; `VarianceAnalysisPanel` |
| 45 | Recurring reporting, governed distribution, browser completion | PASS (UX sign-off open) | `DashboardServiceTest` 4/4 + `ExportFormatServiceTest` 5/5; `DashboardsPanel`/`ReportingWorkspacePanel`; e2e `recurring-reporting.spec.ts` 4/4 + `reporting-mobile.spec.ts` 3/3; `r3-ux-acceptance.md` |
| 46 | Planning migration + R3 acceptance gate | PASS with owner gate open | `run-planning-migration.sh --verify` (PlanningMigrationTest 6/6 — totals/cutoff/replay/independent reconciliation/fallback); `planning-cutover-and-fallback.md`; `r3-parallel-run-report.md`; `verify-release.sh --release R3` PASS; evidence pack delivered |

## Open items

| ID | Item | Owner |
|---|---|---|
| R3-GATE | R3 GO/no-go: packs + statements formally accepted; R4 authorized | All named owners |
| R3-UX | UX acceptance matrix (screen reader, device lab, export fidelity, cross-browser) | Product + Accessibility + Business |
| R3-PERF | Confirmed-population performance + signed workloads (pack/dashboard/drill/monthly template) | Technical + Business |
| O-B1/O-B7/O-B8 | Carried from R0–R2 (golden sign-off, manual accessibility, admin wiring) | Named owners |
| OQ-007 | R4 driver/scenario/allocation/elimination scope recorded at gate | Decision owners |
