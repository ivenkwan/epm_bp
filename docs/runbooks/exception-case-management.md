# Exception Case Management Runbook

## Ownership of state (the invariant)

PostgreSQL owns case state (`epm_case`, Task 14 `V009`). Kogito coordinates durable
next action, actor, timers, and escalation via `processes/bpmn/exception-case.bpmn` and
the `epm_process_correlation` register — it never owns financial or case facts. Timers
are advisory: they may **remind, reassign, escalate, or change visibility** and nothing
else (`epm_case_timer` action check constraint + service guard). Case closure and
business-object certification are separate human decisions.

## State machine (PRD 6.8.4)

```
OPEN → TRIAGED → ASSIGNED → IN_PROGRESS
IN_PROGRESS → WAITING_SOURCE | WAITING_EVIDENCE | WAITING_APPROVAL → IN_PROGRESS
IN_PROGRESS → RESOLUTION_PROPOSED → RESOLUTION_APPROVED → REPROCESSING → VERIFIED
VERIFIED → CLOSED | IN_PROGRESS (closure review rejected)
CLOSED → OPEN (reopen with reason)
```

Guards enforced server-side (`CaseService`):

- triage requires an assigned resolver;
- resolution proposal requires evidence;
- the resolver cannot approve their own resolution;
- the approver cannot verify their own approval;
- the verifier cannot close — closure is independent;
- reopen requires a recorded reason.

Every transition appends immutable evidence to `epm_case_transition` (from/to state,
actor, note, UTC time). Illegal transitions are rejected without state change.

## Timers

Milestones HALF (50%), THREE_QUARTER (75%), BREACH, CUSTOM schedule advisory actions
at 50%/75%/breach per policy. Firing records `fired_at_utc` only — there is no code path
from a timer to approve/close/certify/publish/post/pay/adjust (asserted by tests and by
the allowlisted action set).

## Recovery

- **Restart**: case state is in PostgreSQL; a restarted coordinator resumes from the
  correlation register; no transition replays (duplicate same-state transitions are
  illegal by definition).
- **Reassignment**: the resolver changes before progress; approvals stay bound to the
  actor who performed them.
- **Failed downstream verification**: VERIFIED → IN_PROGRESS returns work to the
  resolver; it never shortcuts to CLOSED.
