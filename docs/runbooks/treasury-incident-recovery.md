# Treasury Incident Recovery Runbook

## Severity and ownership

- Sev-1 (certification at risk / data integrity): accountable platform owner + incident
  commander; break-glass (Task 30) only when needed, dual-approved and reviewed.
- Sev-2 (backlog, lag, single-source failure): owning operator; case opened with
  severity and accountable owner.

## Recovery paths

| Failure | Path |
|---|---|
| Service loss | Declarative redeploy; PostgreSQL-owned state (cases, submissions, certification) survives; process state resumes from the correlation register |
| Database loss | `backup-postgresql.sh` evidence → `restore-postgresql.sh` into an isolated target → `verify-restore.sh` (AGE installed, schema, watermarks, no duplicate effects) → cutover when verified |
| AGE corruption | Destroy and rebuild the graph from relational watermarks (`rebuild-age-projection.sh` / `AgeRebuildTest` discipline); dependent certification blocked until fresh |
| Outbox backlog | `ingestion-backlog-recovery.md`: scale workers, drain (idempotent), catch the watermark, verify |
| Platform cannot certify today | `treasury-manual-fallback.md`: human-declared fallback; no fabricated certification |

## After recovery

1. Replay verification: no duplicate business effects (exactly-once outbox).
2. Reconcile the divergence window before publication resumes.
3. Independent verification, then publication.
4. Post-incident review within 24 hours; evidence to the R2 pack.

## Evidence

Backups carry immutable manifests with SHA-256 digests; restores are verified before
use; RPO/RTO are **measured** against approved policy and recorded — never assumed.
