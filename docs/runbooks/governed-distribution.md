# Governed Distribution Runbook

## Exports

- Exports render **only from immutable certified editions** (published, never draft);
  every file carries the edition id, the `watermark=certified` marker, and the
  correlation id in its header, so any figure traces to its certified source.
- The **configured row limit is enforced with no silent truncation**: an over-limit
  request is refused with a safe reason and directed to a durable export job; partial
  files are never produced.
- Formats: CSV and PDF; figures render exactly as stored (decimal strings — no float
  formatting).

## Distribution

- The recipient is **re-authorized at distribution time** (Task 8 entitlement verdict
  for the edition's entity scope); an unauthorized recipient is denied with a safe
  reason and nothing is logged about restricted content.
- Every delivery is logged in `epm_distribution_log` with the export id, recipient,
  UTC timestamp, retention class (INTERNAL/CONFIDENTIAL/RESTRICTED), and correlation
  id; retention classification drives the archival policy.

## Action register

- Action items carry description, owner, due date, decision, decided-by, completion
  evidence, and independent verification; the verifier must differ from the decider
  (schema CHECK) and evidence must exist before verification passes.
- **Timers remind but never verify or close actions**: due-date reminders escalate
  visibility to the owner; completion and verification are always named human calls.
