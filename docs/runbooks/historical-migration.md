# Historical Migration Runbook (Task 15, R0 design; executed in R1)

Preconditions (all must be green before Step 1):

- [ ] Migration register complete for every class (owner, depth, mappings, totals,
      cutoff, replay rule, archive, fallback, parallel window).
- [ ] Control totals extracted and independently verified for the frozen legacy state.
- [ ] Fallback rehearsal completed within the agreed time box.

## Sequence

1. **Freeze** — legacy feeds paused; freeze timestamp recorded in UTC; late-arriving
   records quarantined to a delta bucket by the cutoff rule.
2. **Final delta extract** — one extract with explicit inclusive/exclusive cutoff per
   the register; the exact-cutoff fixture case proves boundary handling.
3. **Control-total verification** — row count, Σ amounts (decimal strings), and
   natural-key hashes must equal the legacy extract exactly; any difference is a
   blocker.
4. **Load** — batches land through the immutable landing path (Task 11) with replay
   identity active; replays create zero new facts.
5. **Reconcile** — opening balances validated against tolerance; out-of-tolerance is a
   blocker with a case opened (playbook 8).
6. **Parallel-run comparison** — run both platforms for the signed window; daily
   reconciliation retained; blocker/warning defect thresholds measured, not judged.
7. **Go/no-go** — named owners decide and record the decision with the evidence
   bundle; a no-go executes the fallback runbook below.
8. **Unfreeze** — new platform becomes the reporting source; legacy enters read-only
   archive mode.
9. **Retire legacy** (separately approved, later) — retirement decision recorded;
   archive retained permanently.

## Fallback (rollback)

1. Unfreeze legacy feeds; record the rollback decision and actors.
2. Re-point reporting to the legacy platform for the affected classes.
3. Preserve everything already landed (immutable evidence); reconcile the divergence
   window when the migration retries.
4. Post-incident review within the agreed window; register updated with lessons.

## Evidence

Every step appends to the migration register and audit trail (UTC, actor, decision,
artifacts). Nothing is deleted; a failed migration is itself auditable history.
