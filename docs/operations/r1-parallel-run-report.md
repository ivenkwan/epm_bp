# R1 Parallel-Run Report

Status: **infrastructure ready; run not started** — the parallel run executes against
the named representative cohort (OQ-001 decision) and is an owner-gated activity.
This report records the machinery, the control totals, and the acceptance shape so the
run can start the moment the cohort is signed.

## Machinery (delivered and green)

- Migration register (`V025__migration_register.sql`): object class, legacy system,
  depth, opening version, mapping set, control totals (rows + amount), cutoff rule,
  replay identity, archive reference, and the GO/NO_GO/FALLBACK status with the named
  decider.
- `MigrationRunner` (finance-integration): resumable, idempotent batches; control
  totals must match exactly; out-of-tolerance opening balances block with both values
  shown; exact-cutoff records land on exactly one side (inclusive at T0, excluded at
  T0+1s); duplicate replays load zero new facts; the fallback stays active until an
  explicit GO.
- `MigrationReplayTest` 5/5 over the golden migration fixtures (`db/fixtures/migration/`).
- Load path: migrations land through the same source-contract → immutable-landing →
  validation → reconciliation chain as daily data (Tasks 10/11/17/19/20).

## Daily parallel-run protocol

1. Both systems (legacy and platform) receive the same daily submissions.
2. Platform output is reconciled per cohort entity: cash position (§1 goldens),
   headroom (§2), covenants (§3), intercompany (§4) — differences are exceptions with
   evidence, never silent adjustments.
3. Defect thresholds (blocker/warning) and the parallel-run duration are pre-agreed
   OQ-012 values recorded in the register before the run starts.

## Acceptance shape (to be measured — no unmeasured targets claimed)

| Measure | Proposed target | Measured |
|---|---|---|
| Daily template validation | p95 ≤ 30 s | pending run |
| Certified daily position after last valid on-time submission | ≤ 60 min | pending run |
| Migration batch control totals | 100% exact | machinery proven; run pending |
| Fallback availability | active until explicit GO | proven (test) |

## Decisions required to start

- OQ-001 cohort (entities, sources, volumes) signed.
- OQ-012 parallel-run duration + defect thresholds signed.
- Migration register rows completed by the named migration owner.
