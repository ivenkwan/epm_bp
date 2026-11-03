# Cutover and Fallback Runbook (Task 15, R0 design; executed in R1)

Cutover moves the representative treasury cohort onto the platform (Task 16 evidence
gate authorizes it; this runbook executes it). Fallback restores the prior source of
truth inside the agreed time box.

## Pre-cutover checklist

- [ ] R0/R1 evidence gates signed by the named owners.
- [ ] Migration register complete; parallel-run window satisfied with zero blockers.
- [ ] Backup/restore of PostgreSQL verified (point-in-time recovery rehearsed).
- [ ] AGE projection rebuild rehearsed from relational evidence.
- [ ] Legacy extract frozen and control totals verified.

## Cutover sequence

1. Announce the freeze window; freeze submissions at the recorded UTC timestamp.
2. Execute the migration runbook steps 3-8 (totals → load → reconcile → go/no-go).
3. Repoint the reporting consumers to certified platform products.
4. Verify: first daily cycle completes end to end (submission → validation →
   reconciliation → certification) with the cohort's control totals reproduced.
5. Record the cutover decision, actors, and evidence bundle in the register.

## Fallback triggers

- Any blocker in steps 2-4 (control totals, tolerance, cycle verification).
- Explicit owner decision within the go/no-go window.

## Fallback sequence (time-boxed; rehearsed)

1. Freeze new-platform submissions; record the decision.
2. Restore the legacy reporting source for the cohort (read-only archive of the
   divergence window is preserved).
3. Communicate to the cohort; reopen legacy submission channels.
4. Reconcile the divergence window when retrying; the migration register carries the
   retry plan.
5. Post-event review within 24 hours; register and runbooks updated.

## Recovery anchors

- PostgreSQL point-in-time recovery (RPO ≤ 15 min proposal) restores financial facts.
- AGE graph: destroy and rebuild from relational watermarks (Task 21 discipline);
  dependent certification stays blocked until rebuild verification passes.
- Process state: `epm_case` + correlation register resume after service restart
  (Task 14); timers resume advisory-only.
