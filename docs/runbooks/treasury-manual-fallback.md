# Treasury Manual Fallback Runbook

When the platform cannot deliver the certified daily position (incident, failed
controls, or a NO_GO migration decision), the manual fallback keeps treasury reporting
alive without fabricating governance.

## Trigger

- Any severity-one platform incident blocking certification before the business cutoff.
- A BLOCKED migration batch or out-of-tolerance opening balance (GO withheld).
- Explicit owner decision during the G1 window.

## Sequence

1. **Declare** — the operations owner declares fallback for the affected entities and
   date; the declaration (actor, timestamp, scope) is recorded as an audit event.
2. **Re-point submissions** — cohort entities submit through the legacy channel for
   the affected date; the platform's cutoff evaluation keeps recording MISSING/LATE so
   the gap is visible, never silently filled.
3. **Manual position** — Treasury prepares the daily position from the legacy source
   with the same reconciliation discipline (control totals, tolerance); the manual
   worksheet is archived with its evidence and referenced from the incident case.
4. **No fabricated certification** — the platform publishes nothing for the affected
   date; consumers see an explicit "manual fallback in effect" status, not a stale
   certified edition presented as current.
5. **Recover** — on platform restoration: outbox replay, AGE watermark check,
   reconciliation of the divergence window, then resume normal certification. The
   divergence window is reconciled before the next certified edition publishes.

## Invariants

- Fallback is a human-declared state with an owner; no timer or automation triggers it.
- Manual figures never enter the platform as certified data; they are evidence on the
  incident case only.
- The last genuinely certified edition stays available and clearly dated.
