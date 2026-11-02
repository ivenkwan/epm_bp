# Evidence Retrieval Runbook

Auditors and control owners can independently retrieve complete source and change
evidence. Nothing below can mutate evidence: landing tables are guarded by triggers
(Task 5 least-privilege role additionally holds only SELECT/INSERT).

## Original payload retrieval

1. Locate the landing by submission id, run id, or load id in `epm_load_log`
   (`ix_load_log_submission`, `ix_load_log_run`).
2. Read the original bytes from `epm_landed_payload` by `payload_id`; the SHA-256
   `content_hash` is shown alongside for independent verification
   (`LandingService.payloadFor(loadId)` returns bytes + hash together).
3. Both time bases are distinct by design: `source_timestamp` (when the source claims
   the data is from) and `receiving_timestamp` (when the platform landed it, UTC).

## Duplicate review

Two load-log rows may reference one payload (content-addressed). `DUPLICATE_REVIEW`
outcomes are surfaced for review; the distinct submission identities remain
distinguishable — nothing is silently merged or overwritten.

## Version and change evidence

1. Governed revisions: `epm_revision` (object, stable id, revision, effective vs
   recorded time, actor, reason, correlation, payload hash).
2. Change evidence: `epm_audit_event` (old/new values, approvals, actor, action,
   reason, correlation; immutability guarded by trigger + revoked privileges).
3. Tamper attempts: `epm_audit_violation` records every attempted UPDATE/DELETE on
   audit or landing evidence; the evidence rows themselves never change.

## Correlation

`correlation_id` joins a business action across revisions, audit rows, outbox events,
and telemetry. Start from the load log's receiving timestamp window or the correlation
id to reconstruct the full chain.
