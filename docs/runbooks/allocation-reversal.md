# Runbook: Allocation Reversal (Task 49)

Controlled reversal of an executed multi-step/step-down allocation run.

## Invariants

1. `epm_allocation_entry` is immutable — reversal never edits or deletes entries.
2. A reversal is a **linked compensating run** (`epm_allocation_run.reversal_of`)
   whose entries are exact negations of the original, each traced as
   `reversal of <runId>`.
3. Replays are idempotent: re-executing the same `run_id` loads no new entries.
4. Each step preserves its allocated total exactly (largest-remainder residual at
   scale 2); step-down excludes earlier senders and self-allocation.
5. Net effect of a run plus its reversal is exactly 0.00; a reversal cannot itself
   be reversed (open a new approved model run instead).

## Steps

1. Identify the run and its model (`epm_allocation_run`, version `alloc-1.0.0`).
2. Obtain reversal approval (operator + independent approver per policy).
3. Execute the compensating run referencing the original `run_id`; verify per-entry
   negation and the exact-zero net total.
4. Post-run: receivers' balances reflect the compensation; reports re-derive from
   ledger entries — no source fact is ever overwritten.

## Failure handling

- Unknown source pool or out-of-sequence step: the model is refused before any entry
  lands.
- Zero pools allocate nothing (legitimate no-op) — verify intended pool wiring.
- Partial execution never occurs: validation precedes entry materialization.
