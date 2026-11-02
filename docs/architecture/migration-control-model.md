# Historical Migration Control Model (Task 15, R0 design)

Design contract for legacy-system migration (F-042). Execution is R1; the design signs
the interfaces the migration must implement.

## Migration register

Every migrated object class has a register row: legacy system, object class, historical
depth, opening version, mapping set (versioned), control totals, cutoff rule, replay
identity rule, archive reference, fallback plan, parallel-run window, go/no-go decision,
and the named retirement approver for the legacy feed.

## Controls

1. **Cutoff inclusion** — explicit inclusive/exclusive statement per class; the
   exact-cutoff record case is in the fixtures and must land on exactly one side.
2. **Control totals** — per class per period: row count, Σ amount (decimal string),
   and hash of the natural-key set; the load must reproduce them exactly.
3. **Replay identity** — replays are detected by (class, natural key, source hash);
   a replayed batch creates no duplicate landed facts (Task 11 idempotent landing).
4. **Opening balances** — out-of-tolerance opening balances stop the migration (the
   fixture case); tolerance is a governed, versioned threshold, not an operator
   override.
5. **Parallel run** — the legacy and new platform run side by side for the signed
   window; daily reconciliations are retained as evidence; defect thresholds
   (blocker/warning) are pre-agreed and measured.
6. **Go/no-go** — an explicit human decision by the named owners recorded in the
   register with the evidence bundle reference; no timer or automation decides.
7. **Legacy retirement** — a separate, explicitly approved decision after the
   parallel-run window; retirement evidence is archived permanently.
8. **Fallback** — rollback to the legacy feed is rehearsed and time-boxed; the runbook
   below is executed at least once in test before cutover.

## Cutover sequence (see runbook)

freeze → final delta extract → control-total verification → load → reconcile →
parallel-run comparison → go/no-go → unfreeze → retire-legacy (later, separately).

## Fixture arithmetic

`db/fixtures/migration/` carries the synthetic migration batch with the expected
control totals, the out-of-tolerance opening balance, the exact-cutoff record, and a
duplicate-replay batch whose expected effect is zero new facts.
