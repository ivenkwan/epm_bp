# R2 Build Status

Per-task results for the R2 scale-and-cutover release (Tasks 35–38). Human decisions
live in `docs/operations/r2-go-no-go-decision.md`.

| Task | Scope | Result | Evidence |
|---|---|---|---|
| 35 | Scale onboarding + capacity/failure gates + backlog recovery | PASS (population workload owner-gated) | `r2-onboarding-register.md` + `onboard-treasury-scope.sh` (fail-closed wave checks); new `tests/performance` module: `PeakBacklogTest` 6/6 (concurrency exact, replay identical, contention 1-win, 20k lossless drain, watermark catch-up, late/missing governed); `ingestion-backlog-recovery.md`; test env config + overlay with autoscaling |
| 36 | Optional stock/unit liquidity context (F-022) | PASS (deferral recorded, fail-closed) | `V026`; `StockUnitContextTest` 5/5 (disabled default throws; zero/blank semantics; source+freshness labels; reconciliation; denied scope); e2e 2/2 (disabled visible; no leakage); `r2-optional-scope-decision.md` |
| 37 | Recovery, handover, controlled cutover | PASS (production restore rehearsal owner-gated) | backup/restore/verify-restore scripts (manifests + SHA-256, env-only credentials, AGE-first); `TreasuryRecoveryTest` 2/2 (clean-container restore, replay refused, one effect); cutover phases rehearsed green; `treasury-cutover.md` + `treasury-incident-recovery.md` + `r2-operational-readiness.md` |
| 38 | R2 acceptance + cutover gate | PASS with R2-M1 + go/no-go open | Reactor 186/0/0 (7 modules) + platform smoke 5/5 + e2e 13/13; all 7 quality/operations gates PASS; `verify-release.sh --release R2` PASS; evidence pack + go/no-go decision + legacy-retirement register delivered; no unmeasured claim |

## Open items

| ID | Item | Owner |
|---|---|---|
| R2-M1 | Confirmed-population workload (2× peak, return-to-normal, parallel-run evidence) | Business + Technical |
| O-B1/O-B7/O-B8 | Golden sign-off; manual accessibility; admin wiring | Named owners |
| OQ-004/005 | Partial-publication rules; forecast parameters | Decision owners |
| Handover | Training, severity exercise, support access, witnessed production restore (RPO/RTO) | Operations |
