# Treasury Cutover Runbook

Phases execute strictly in order via `scripts/operations/cutover-treasury.sh <phase>`;
each phase fails closed. The go/no-go decision is always a named-human act.

## Preconditions

- [ ] G1 signed **scale**; R2 waves onboarded and reconciled (onboarding register).
- [ ] Parallel run complete with zero blockers (`r1-parallel-run-report.md` updated).
- [ ] Backup/restore verified (`verify-restore.sh` green) with measured RPO/RTO.
- [ ] Cutover dress rehearsal completed once, including rollback.

## Phases

### 1. freeze
Pause submissions at a recorded UTC timestamp; late arrivals quarantine to the delta
bucket per the cutoff rule; the freeze actor and timestamp go into the cutover log.

### 2. verify
Control totals, parallel-run comparison, and recovery drills must all pass
(`verify-migration.sh`, `verify-r1-recovery.sh`). Any failure aborts to rollback.

### 3. cutover
Re-point reporting consumers to certified platform products; verify the first daily
cycle end to end (submission → validation → reconciliation → certification). **Human
gate:** named owners record go/no-go with the evidence bundle reference. No automation
decides.

### 4. rollback (if no-go or post-cutover failure)
Freeze new-platform submissions; restore legacy reporting for the cohort; the
divergence window is preserved as immutable evidence and reconciled on retry;
post-event review within 24 hours.

## Invariants

- Financial corrections after recovery are **controlled new entries only** (F-035
  four-eyes ledger); no direct edits, no destructive overwrites.
- Fallback stays available for the authorized interval after go-live.
- Legacy retirement is a separate, later, explicitly approved decision — technical
  go-live is never retirement approval.
