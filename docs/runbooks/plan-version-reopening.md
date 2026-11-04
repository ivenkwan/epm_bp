# Runbook: Plan Version Reopening (Task 41)

Controlled reopening of a locked Budget/Forecast version. Reopening is a governed,
multi-human act; no timer, batch job, or automation may reopen, approve, or lock.

## Invariants

1. A LOCKED version is immutable — there is no direct-edit path, for any role.
2. Reopening requires a **nonblank reason** and a **nonblank scope**, from an actor
   holding the reopen-requester entitlement.
3. An approved reopen creates a **new revision** (revision + 1, DRAFT, seeded from the
   prior plan with `prior-plan:<planId>:r<rev>` cell lineage). The prior approved
   version is preserved unchanged as SUPERSEDED with its content hash intact.
4. Empty seeding is a no-data outcome; an empty plan can never be submitted or
   approved.
5. Approval is independent (never the owner) and the final approval locks.

## Steps

1. **Verify the lock.** Confirm the version is LOCKED and note its content hash
   (`epm_plan_version.content_hash`).
2. **Request reopening.** An entitled requester records the request
   (`epm_plan_reopen_request`) with reason and scope. Blank reason/scope is refused by
   the database CHECK constraints and by the service.
3. **Decide the request.** A controller (not the requester where policy requires
   independence) approves or rejects. Approval only authorizes a new revision — it
   does not itself unlock anything.
4. **Work the new revision.** Editors edit cells (entitlement + revision-checked;
   stale revisions are refused — reload and retry). Seeded cells carry source lineage.
5. **Submit and relock.** Submit; an independent approver approves; the final
   approval locks revision N+1 and records its own content hash.
6. **Compare.** Cell-level comparison of the superseded and new revisions documents
   exactly what changed and why (linked to the reopen reason).

## Failure handling

- Second seeding attempt on any revision: refused ("seeding happens exactly once").
- Edit against LOCKED/SUBMITTED: refused regardless of role.
- Approver == owner: refused as self-approval.
- Restart mid-cycle: all durable state is in PostgreSQL (`epm_plan_version`,
  `epm_plan_cell`, `epm_plan_approval`, `epm_plan_reopen_request`); decisions are
  re-derived from records — no in-memory authority exists.

## Records

| Record | Table |
|---|---|
| Version + status + hash | `epm_plan_version` |
| Cells + lineage | `epm_plan_cell` |
| Assumptions | `epm_plan_assumption` |
| Approval chronology | `epm_plan_approval` |
| Reopen requests + decisions | `epm_plan_reopen_request` |
