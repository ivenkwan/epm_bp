# R2 Operational Readiness

Status: **engineering ready; handover owner-gated.** This record maps each readiness
item to its delivered evidence and names the human acceptance where one is required.

| Item | Evidence | State |
|---|---|---|
| Exception-state operational coverage | Case state machine + guards (`CaseRecoveryTest` 10/10); delegation expiry / break-glass review (`BreakGlassTest` 7/7); exports/distribution/actions (`ExportEntitlementTest` 4/4); failed-verification and reopen paths tested at the owning services | Delivered |
| Backup / restore | `backup-postgresql.sh` (manifests + SHA-256 digests, credentials from env only); `restore-postgresql.sh` (AGE installed first); `verify-restore.sh` (AGE, schema ≥30 tables, no duplicate certification, recovery suites) | Delivered; rehearsal below |
| Restore rehearsal into a clean environment | R0: 33/33 schema tables restored into a scratch container; `TreasuryRecoveryTest` (below) restores into a scratch AGE container and re-verifies | Delivered (synthetic); production-shaped RPO/RTO measurement owner-gated |
| AGE consistency on primary/replica/restore | Same `apache/age` image everywhere (`version-matrix.md` digests); restore script installs the extension before data | Delivered |
| Outbox replay / process & timer verification | `OutboxConcurrencyTest` 7/7 (redelivery exactly-once); advisory-timer allowlist + tests (T14/T30) | Delivered |
| Cutover dress rehearsal | `cutover-treasury.sh` phases (freeze/verify/cutover/rollback) + `treasury-cutover.md`; rollback rehearsal = the rollback phase executed on the rehearsal cluster | Scripted; full dress rehearsal runs with the cohort |
| Manual fallback | `treasury-manual-fallback.md` (human-declared, no fabricated certification) | Delivered |
| SLO / cost dashboards, alert routes | Governance labels on telemetry (platform smoke); autoscaling + saturation policy in `config/environments/test.yaml`; dashboard deployment is the operators' environment task | Config delivered |
| Training + adoption record | Representative users/operators training sessions | **Owner-gated** |
| Severity exercises | Incident runbook severity model; exercise execution | **Owner-gated** |
| Support access / break-glass | Dual-approved break-glass with 24h review; support access provisioning per owner policy | Controls delivered; access list **owner-gated** |

## Handover checklist (owners)

- [ ] Operators complete training; adoption + manual-workaround findings recorded.
- [ ] Severity exercise executed and logged.
- [ ] Support-access list approved; break-glass reviewers named.
- [ ] SLO/cost dashboards deployed to the operators' environment and acknowledged.
- [ ] Production-shaped restore rehearsal witnessed; measured RPO/RTO recorded.
- [ ] Treasury operations sign acceptance in `r2-go-no-go-decision.md`.
