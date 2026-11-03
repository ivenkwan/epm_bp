# Source Contract Schema

Authoritative registration of every data source feeding the platform (Task 10;
`db/migrations/V006__source_contracts.sql`).

## Source system (`epm_source_system`)

| Field | Meaning |
|---|---|
| `system_id` | Stable identifier (`SYS-*`) |
| `system_name` | Human-readable name (synthetic in R0 fixtures) |
| `owner` | Named accountable owner for the source |
| `kind` | `ERP`, `BANK`, `SPREADSHEET`, `API`, `MARKET_DATA` |

## Contract versions (`epm_source_contract`)

Versioned rows keyed `(contract_id, version)` with the full contract content:

- **Ownership**: `system_id` + `owner` (the source owner, distinct from the steward).
- **Scope and grain**: `scope` (declared population) and `grain` (e.g. `ENTITY_ACCOUNT_DAY`).
- **Cadence and cutoff**: delivery rhythm and the business cutoff (UTC time).
- **Fields**: `field_definitions` jsonb — field name → governed type (`MONEY`, `FX_RATE`, ...).
- **Quality**: `quality_rules` jsonb — completeness/type/range expectations per field.
- **Reconciliation**: `reconciliation_basis` jsonb — control totals and tie-out definition.
- **Retention**: `retention_months` (84 for synthetic fixtures).
- **Credentials**: `credentials_ref` — a secret *reference*; never a literal credential.
- **Precedence**: lower wins; resolves otherwise-equal scope claims.
- **Status**: `PROPOSED → APPROVED → ACTIVE → DEPRECATED` (or `REJECTED`).

## Lifecycle and invariants

1. Exactly one ACTIVE contract is authoritative per scope; a hard index cannot express
   the atomic swap a precedence supersedes, so the invariant is enforced in
   `SourceContractService` (activation with equal precedence on a different contract is
   rejected as ambiguous; better precedence deprecates the incumbent with evidence).
2. The proposer cannot approve their own contract; `approved_by` is recorded.
3. Every transition appends to `epm_source_contract_transition` (from/to status, actor,
   reason, UTC) — immutable evidence.
4. Optional fields may be omitted at registration; required fields (id, scope, grain,
   cutoff) fail with **field-level** violation messages.

## R1 decisions required (OQ-002)

Before activation in R1, source owners must record: direct-feed vs template decision,
expected latency, and control totals — the activation step refuses to complete without
them once recorded as required in the R1 contract checklist.
