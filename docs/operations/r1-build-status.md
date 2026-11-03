# R1 Build Status

Per-task build/test/verification results for the R1 lighthouse (Tasks 17–30) plus the
O-B6 unblock, recorded as work completes. Human sign-offs live in
`docs/operations/r1-evidence-pack.md`.

| Task | Scope | Result | Evidence |
|---|---|---|---|
| — | O-B6 durable-job runtime (Task 6 deferred contract) | PASS | `JobService`/`JobEntity` in finance-integration (Panache); `JobServiceTest` 1/1: replay returns the same job, exactly-once claim (QUEUED→RUNNING counter), scheduler-driven worker |
| 17 | Scheduled ingestion with replay safety | PASS | `V010` ingestion runs (unique schedule+batch identity); `ScheduledIngestionTest` 5/5 (due-run lands with freshness; duplicate extract → original run, one payload, no duplicate facts; zero rows → completeness exception; operator replay idempotent + named-operator required; connector allowlist closed) |
| 18 | Template intake + controlled resubmission | PASS (benchmarks pending) | `V011` template submissions (anchor+revision, row errors); `TemplateSubmissionServiceTest` 5/5 (valid lands; coordinate errors; header mismatch rejected pre-rows; exact-max accepted / max+1 rejected pre-parse; corrected resubmission = revision 2 with original preserved) |
| 19 | Deterministic validation + quarantine | PASS | `V012` validation runs + quarantine rows; `ValidationBoundaryTest` 4/4 (adversarial fixtures quarantined with rule/expected/actual/explanation; zero + negative-payment pass; reproducible by version; duplicate natural key row 2); validation fact shared with the R0 governance suite |
| 20 | Reconciliation + independent certification | PASS | `V013`; `CertificationControlTest` 6/6 (inclusive exact-tolerance; over-tolerance blocks; fails closed on missing evidence/unexplained difference/blocking case/self-certification; one decision per revision; rejections survive rollback via REQUIRES_NEW writer) |
| 21 | Rebuildable AGE lineage | PASS | `V014` provenance; `AgeRebuildTest` 3/3 (projection + downstream traversal; rebuild equivalence; stale watermark blocks); rebuild script + verify SQL |
| 22 | Daily treasury calendar + cutoffs | PASS (OQ-004 hold default) | `V015`; `DailyCutoffTest` 6/6 (T0 inclusive, T0+1s late, missing=null, prior-period exclusion, UTC wall clock, pure reclassification); runbook |
| 23 | Certified cash position | PASS (latency measurement pending) | `V016`; `CashPositionTest` 6/6 vs §1 goldens (every account row, group 1941.00 at FX-V1, continuity, overdraft, float trap); `cash-calculation.md` |
| 24 | Facilities/headroom | PASS | `FundingHeadroomTest` 4/4 vs §2 goldens (374999.50, exact-zero boundary, −1.00 anomaly explicit, deposits reconcile); `facility-maintenance.md` |
| 25 | Intercompany matching | PASS | `IntercompanyMatchingTest` 5/5 vs §4 goldens (MATCHED 0.00, MISMATCHED 5000.00, boundary inclusive, currency distinct, both sides preserved, ageing); `intercompany-mismatch.md` |
| 26 | Covenant monitoring | PASS | `CovenantBoundaryTest` 5/5 vs §3 goldens (0.500000, inclusive 1.200000, zero-denominator INVALID with null ratio, boundary±ε, DMN agreement); covenants DMN + runbook |
| 27 | Rolling cash forecast | PASS (OQ-005 values pending) | `RollingForecastTest` 5/5 (roll-forward exactly once, prior-close linkage rejects mismatch, not-yet-due untouched, variance, empty-schedule rejection); `cash-forecast-model.md` |
| 28 | Refresh/commentary/republication | PASS | `V021` editions + comments; `PublicationServiceTest` 6/6 (named publisher, resolved comments, failed controls leave last certified available, citation rejection, supersede/revoke semantics); runbook |
| 29 | Adjustment ledger four-eyes | PASS | `V022` ledger; `AdjustmentControlTest` 5/5 (balance, four-eyes, materiality boundaries 999.99/1000.00/9999.99/10000.00, DMN agreement, reversal net-zero); approval-level DMN |
| 30 | R1 evidence gate | PASS with sign-offs open | Reactor 157/0/0; all four quality gates PASS; fixture manifest 39 digests; platform smoke 5/5; frontend 15/15 + build; e2e 7/7; `verify-release.sh --release R1` PASS; golden fidelity table §-by-§ in the evidence pack |

## Open items (carried)

| ID | Item | Owner |
|---|---|---|
| O-B1 | Finance/Treasury sign-off of goldens + control designs (all calculators now reproduce them) | Finance/Treasury owners |
| O-B8 | Admin browser wiring (operator/steward/case/publication views) | Engineering |
| OQ-004 / OQ-005 | Partial-publication rules; forecast parameters (safe defaults enforced meanwhile) | Named decision owners |
| R1-M1 | Representative-cohort parallel run + latency/throughput measurements | Owners + Engineering |
