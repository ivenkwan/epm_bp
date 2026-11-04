# Runbook: Planning Cycle (Task 43)

Operating the planning calendar: milestones, task ownership, multi-level approvals,
reminders, overdue escalation, and the final lock.

## Invariants

1. Approvals progress **level by level**; level N+1 is not actionable until level N is
   completed by its assignee.
2. Every approval level requires an **independent eligible actor** — the preparer can
   never approve any level (self-approval denied before any other check).
3. Reminders and escalations change **visibility only**: no timer ever approves,
   locks, publishes, or closes anything.
4. Rejection (RETURN) sends the cycle back to preparation and **cancels stale
   higher-level tasks**; resubmission creates a fresh level sequence.
5. The **final lock** is a named-human act completing the last required level; the
   locked cycle refuses further actions (reopening goes through plan-version
   reopening, Task 41).
6. All durable state is Java/PostgreSQL-owned (`epm_planning_cycle`,
   `epm_planning_task`, `epm_planning_decision`); decisions form an append-only
   chronology with comments.

## Steps

1. **Open the cycle** with the signed FP&A policy: milestones, level assignees, due
   dates, and the business calendar.
2. **Prepare** — the preparer assembles schedules (seed exactly once) and submits.
3. **Approve level by level** — each assignee reviews and approves with comment;
   delegated tasks record who delegated to whom (independence still applies).
4. **Reminders** — inside the lead window a reminder is recorded once per task.
5. **Breach** — at/past due the task escalates for visibility; the assignee can still
   complete it (a human act).
6. **Return** — a rejected level returns the cycle to preparation; stale open tasks
   are cancelled; rework then resubmit.
7. **Final lock** — the last level's approval locks the cycle and the plan version
   together.
8. **Measure** — cycle timestamps are measured against the stabilized five-business-
   day target; the target is reported, never assumed achieved.

## Process definition

`processes/bpmn/planning-cycle.bpmn` (prepare → level 1 → level 2 → final lock) and
`processes/bpmn/plan-version-approval.bpmn` run on the embedded Kogito runtime
(LightProcessRuntime + StaticProcessConfig). Both are proven by
`tests/integration/process/PlanningCycleTest`: named-human sequencing, no advance
while a required approval waits, restart from definitions, and no service tasks or
timer-driven transitions anywhere in the definitions.
