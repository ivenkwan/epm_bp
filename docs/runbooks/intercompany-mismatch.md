# Intercompany Mismatch Runbook

## Matching (rule `ic-1.0.0`)

Counterparty sides pair on **stable counterparty entity ids** (A lends to B ↔ B lends to
A), then on currency, rate, and tenor; principal compares under an absolute tolerance
(scale 2, default 0.01 — inclusive boundary: a difference exactly at the tolerance
matches).

## Outcomes

| Status | Meaning | Next action |
|---|---|---|
| `MATCHED` | Same pair, same terms, principal within tolerance | No action |
| `MISMATCHED_PRINCIPAL` | Same pair and terms, principal beyond tolerance | Treasury review with the difference and ageing visible |
| `MISMATCHED_TERMS` | Currency/rate/tenor differ | Treasury review; both term sets shown |
| `MISMATCHED_COUNTERPARTY` | No counterparty side exists | One-sided position queue |

## Invariants

- **No side is ever overwritten**: both loan ids remain identifiable in every result.
- **No automatic elimination**: a mismatch queues for Treasury; management-only
  eliminations are a planning feature (R3) and never a statutory representation.
- Mismatches carry the principal difference and ageing (days since the later recorded
  date) so reviewers see materiality and staleness together.
- Resolution flows through exception cases (Task 14) with entity explanation, Treasury
  review, resolution evidence, and independent verification; business-object
  certification stays a separate decision.
