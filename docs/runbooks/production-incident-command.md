# Runbook: Production Incident Command (Task 56)

Command structure and playbooks for production incidents. Deterministic finance
operations never depend on AI; disabling any AI route is a first-move option, not a
last resort.

## Command roles

| Role | Responsibility |
|---|---|
| Incident commander | Owns the incident, timeline, and decisions; single voice |
| Operations lead | Executes technical actions (disable, rollback, restore) |
| Finance control | Validates financial integrity before/after any recovery |
| Communications | Stakeholder and user messaging |

## Severity and first moves

| Severity | Examples | First moves |
|---|---|---|
| S1 | Data integrity doubt, publication of wrong figures, leak | Freeze publications; kill AI routes (`ai.enabled: false`); open an integrity case; preserve evidence |
| S2 | Platform outage, ingest backlog beyond SLA | Start recovery runbooks (AGE rebuild, outbox replay, restore); fallback templates active |
| S3 | Degraded performance, single-source failure | Contain; monitor; scheduled remediation |

## Playbooks (references)

- Backup/restore + replay refusal: `scripts/operations/backup-postgresql.sh`,
  `restore-postgresql.sh`, `verify-restore.sh`, `TreasuryRecoveryTest`
- AGE lineage rebuild + watermark: `rebuild-age-projection.sh`, `verify-r1-recovery.sh`
- Treasury cutover/rollback: `cutover-treasury.sh`
- Planning cutover/fallback: `planning-cutover-and-fallback.md`
- AI disablement (≤5 min target): `ai-disablement.md`
- Master data / mapping rollback: `master-data-change.md` (rollback = new version)
- Allocation reversal: `allocation-reversal.md`
- Rule/prompt promotion rollback: `model-prompt-process-promotion.md`

## Non-negotiables

1. Never edit financial facts to "fix" an incident — corrections go through the
   governed ledgers (adjustments, reversals, new versions).
2. Never re-enable an AI route during an incident involving it.
3. Every incident closes with a review: timeline, evidence, corrective actions, and
   updated runbooks (living documentation).
4. Evidence is immutable: export the provenance/audit trail before remediation.

## Verification

`./scripts/operations/verify-final-recovery.sh` must pass after any recovery exercise.
