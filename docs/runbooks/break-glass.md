# Break-Glass Runbook (Playbook 12)

## Delegation (scoped, time-bound)

- A delegation names **delegator, delegate, entity scope, and permitted action**, with
  an explicit validity window. Decisions attribute authority to the original owner.
- **Non-transferable**: no code path exists to re-delegate a held delegation — by the
  delegate or anyone else.
- **Automatic expiry**: at the window end, delegated authority ends without completing
  any approval or changing business state; revocation has the same effect.
- Escalation is advisory: it raises visibility (queues, owners) and never grants
  authority.

## Break-glass sessions

1. **Open**: requester + independent approver (two distinct individuals) + scope +
   written justification. Individual MFA authentication is the identity-provider
   contract (Task 8 edge).
2. **Expiry**: 60 minutes from start (proposed target; measured and formally approved
   before scale). Early revocation ends the session immediately; nothing in flight
   completes and no standing privilege remains.
3. **Always denied**, even inside a session: payment, GL posting, certification,
   publication, autonomous approval, and direct financial edits outside the F-035
   four-eyes ledger. Self-approval and self-closure are denied. Every decision
   (allow and deny) is recorded with actor, action, and timestamp; sensitive session
   content is never logged.
4. **Independent review within 24 hours** by a reviewer distinct from the requester;
   the review decision and follow-ups are recorded on the session.

## Evidence

All sessions and in-session actions live in `epm_break_glass_session` /
`epm_break_glass_action` (immutable audit discipline); delegations live in
`epm_delegation` with revocation timestamps. Security/Control owners sign the
privileged-access evidence at the gate.
