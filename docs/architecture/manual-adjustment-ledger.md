# Manual Adjustment Ledger — Control Design (Task 15, R0 design)

Design contract for the R1 adjustment engine (Task 29 implements it). Nothing here is
executable yet; `ControlDesignContractTest` validates the contract arithmetic against
the fixtures below.

## Ledger model

An adjustment is a **proposal** of balanced lines (debits = credits, fixed-precision
decimal strings, currency + scale + rounding explicit) with:

- **Preparer, independent approver, independent verifier** — three distinct named
  people (four-eyes minimum; verifier closes the case, a separate decision from any
  business-object certification).
- **Materiality/approval level** — selected by the governed DMN (Task 29
  `rules/dmn/adjustments/approval-level.dmn`): LOW → owner review; MEDIUM → independent
  entity approver; HIGH → domain approver **plus** certifier/publisher.
- **Evidence** — every proposal cites source revisions, load ids, and reconciliation
  statements; missing evidence blocks submission (case playbook 5).
- **Application transaction** — approval applies the ledger in one authoritative Java
  transaction that also emits the outbox event; no AI and no timer has application
  authority (timers only remind/escalate per Task 14).
- **Adjusted bridge** — reported-basis → adjusted-basis bridge preserved per line with
  the approval reference.
- **Linked compensating reversal** — a reversal is a new, linked, balanced proposal
  referencing the original; original, publication, approval, and reversal evidence are
  retained forever. There is **no destructive update** anywhere in the design.

## State model

```
PROPOSED → APPROVED → APPLIED → (REPORTED basis refreshed)
APPLIED → REVERSAL_PROPOSED → REVERSAL_APPROVED → APPLIED(reversal)
PROPOSED/REVERSAL_PROPOSED → REJECTED (evidence retained)
```

Rejected proposals never change reported facts. Application reprocesses affected
reconciliation and outputs before anything republishes.

## R1 interface contract (signed at R0 gate)

| Element | Contract |
|---|---|
| `AdjustmentProposal` | lines[] (account, decimal string, D/C), currency, scale, HALF_UP, reason ≥ 1 char, evidence[] non-empty, entity scope |
| Balance invariant | Σ debits = Σ credits per currency, per entity, per proposal |
| Approval level | materiality DMN over `abs(Σ line amount)` and entity materiality table |
| Application | single Java transaction: ledger row + adjusted bridge + audit + outbox event |
| Reversal | linked proposal; restores prior reported basis; retains all four evidence sets |

## Synthetic worked example (fixture arithmetic)

`db/fixtures/migration/adjustment-example.csv` carries balanced/unbalanced proposals and
the independently expected totals, including the out-of-tolerance rejection,
exact-boundary approval, and post-publication reversal cases. The expected values are
hand-calculated in `expected-adjustment-example.csv` (decimal strings, scale 2).
