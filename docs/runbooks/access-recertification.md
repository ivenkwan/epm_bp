# Access Recertification Runbook

## Cadence and ownership

- Entity scopes carry `review_due_utc` (default: grant date + 3 months).
- The control owner (per `docs/operations/control-ownership-matrix.md`) opens review
  periods via `POST /api/v1/admin/access-reviews` and an **independent reviewer** signs
  them; a reviewer may not sign their own access.
- Overdue reviews **never grant or extend access**; enforcement denies the scope and
  raises an `ACCESS_REVIEW_OVERDUE` escalation visible to the owner.

## Procedure

1. List scopes due: query `epm_entity_scope` where `review_due_utc <= now()` and
   `revoked_at_utc is null`.
2. Independent reviewer re-confirms business need per (user, entity); revocations set
   `revoked_at_utc` (grants are never deleted — history is immutable).
3. Signed reviews record reviewer and signed-at on `epm_access_review`.
4. Escalations older than 30 days without a signed review escalate to the security
   owner; the escalation row is resolved only by a signed review or revocation.

## Evidence

Every allow and deny is captured in `epm_access_decision` (user, roles, object, action,
entity, decision, reason, correlation id, UTC time). Attempted SoD violations and
overdue reviews also raise `epm_escalation` rows. Auditors query these tables directly;
nothing in the browser or gateway can suppress an audit row.
