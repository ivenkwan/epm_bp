# Management Report Model (Task 39)

Management reports and initial decision packs assemble **only** from certified data
products, shared KPI figures from the governed semantic catalog, actuals/plans
references, and approved commentary. There is **no report-local KPI calculation** —
every figure is a reference, never a formula.

## Objects

| Object | Meaning |
|---|---|
| `epm_management_report` | Enterprise/division/entity report definition with scope, layout (FLASH / OPERATIONAL / BOARD), owner, version, status (DRAFT / CERTIFIED / RETIRED) |
| `epm_report_section` | Ordered sections per report; `required` sections must carry figures; comparison basis (PRIOR_PERIOD / BUDGET / FORECAST) is a reference, never derived |
| `epm_report_figure` | Certified shared figure with governed `activity_state` |
| `epm_decision_pack` | Pack configuration (layout, owner, status, current edition number) |
| `epm_pack_member` | Report membership; `required` members must be CERTIFIED |
| `epm_pack_edition` | Immutable generated edition with deterministic `content_hash` |
| `epm_pack_approval` | Append-only approval chronology per edition |

## Governed semantics

1. **Shared-figure equivalence.** The same KPI appearing in multiple member reports
   must carry the same value; divergence proves report-local recalculation and blocks
   generation (`shared figure divergence`).
2. **Zero activity vs missing data.** `amount = 0` certifies as `ZERO_ACTIVITY` (a
   legitimate governed value); an absent figure is `MISSING` and blocks certification
   inside required sections. Missing never renders as zero.
3. **Scope certification.** Every report scope key (`ENTERPRISE`, `DIVISION:<id>`,
   `ENTITY:<id>`) must be certified in the certification ledger; otherwise
   certification fails closed (`required scope uncertified`).
4. **Retired KPI references.** Figures referencing RETIRED or UNKNOWN catalog entries
   block certification — retired cards cannot be cited by new reports.
5. **Empty required sections.** A required section with no figures blocks
   certification.
6. **Opening-balance reconciliation.** Board-style packs require the opening balance
   to reconcile to the prior certified closing before an edition is generated.

## Editions and approval history

- Generation produces an immutable edition (deterministic SHA-256 content hash over
  canonical member figures, balances, owner, layout). Regeneration from identical
  inputs reproduces the same hash — output equivalence is testable (`reproduce`).
- Approvals are recorded as an append-only chronology against an edition number;
  approving or rejecting never rewrites edition content. Each pack keeps its own
  independent edition sequence and approval history.
- Surfaces show version (edition number), freshness (`as_of_utc`), certification,
  owner, and approval state for every pack (AC-F-012 journeys).

## Boundaries

- Timers and automation never approve or publish packs; approval is a named-human act.
- The standard approved content/export workload measurement (AC-F-011 timing) is an
  owner-gated measurement recorded in the release evidence pack, not a claim made here.
