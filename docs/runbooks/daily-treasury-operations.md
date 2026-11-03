# Daily Treasury Operations Runbook

## Calendar and cutoffs

Every entity has a row in `epm_treasury_calendar` (timezone, UTC cutoff, accountable
owner). The cutoff instant for a business date is the UTC wall clock
(`TreasuryCalendarService.cutoffFor`).

## Boundary semantics (the contract)

- A receipt **at exactly T0 is on time** (inclusive boundary).
- A receipt at **T0+1s is late** — and stays late; the original receiving timestamp is
  preserved verbatim, never re-stamped.
- A **missing** submission (null receipt) is `MISSING`, distinct from late.
- A submission from a **prior period never counts for today**, even if it arrives
  today: only same-period receipts with an on-time-or-late outcome update the daily
  state (`countsForPeriod`).

## Daily cycle

1. **Pending** — the day opens with every scoped entity `PENDING`.
2. **Receipt** — submissions land through ingestion/templates; each receipt is
   classified (`ON_TIME` / `LATE_AT_T0_PLUS`) against the entity cutoff.
3. **Cutoff evaluation** — at the cutoff the completion matrix is evaluated
   (`epm_cutoff_evaluation`): on-time, late-at-T0+, or missing per entity, with the
   accountable owner, last valid revision, and affected outputs.
4. **Late/missing queues** — late and missing entities enter owned queues; the
   accountable owner decides hold / partial / defer under the approved partial-
   publication rules (OQ-004 decisions). No timer approves, closes, or publishes.
5. **Certification eligibility** — the day is eligible only when every required scope
   is on time, late-with-explanation, or explicitly governed.

## Timers

Timer milestones (50%/75%/breach) remind and escalate only. Reclassification is pure:
the same receipt always classifies identically (`DailyCutoffTest` reclassification
test); timers never mutate classification, approval, or publication state.

## Partial publication (OQ-004)

Hold/partial/defer decisions are human decisions recorded with owners and reasons;
until the signed OQ-004 rules land, the default is **hold**: no partial certified
position is published.
