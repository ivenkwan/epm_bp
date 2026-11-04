# Master-Data Change Runbook

## Scope

Governed changes to entities, charts, cost/profit centers, management dimensions,
mappings, and hierarchy versions (`MasterDataService`).

## Steward journey

1. **Propose** — the steward prepares a change (new entity version, mapping, or
   hierarchy version) with actor, reason, and effective interval.
2. **Compare** — old/new values and the interval diff are shown from audit evidence;
   nothing overwrites history: versions are rows, and rejected proposals change no
   active version.
3. **Approve & activate** — an independent approver activates the version. Activation
   validates the whole assignment set: overlapping intervals, self-parenting, and
   cycles are rejected before any row lands (database guards back the service checks).
4. **Roll back** — rollback activates a previously superseded version as a new
   effective-dated row; the prior version's history is never deleted.

## Interval semantics

- `effective_from` is inclusive; `effective_to` is exclusive.
- Adjacent versions **share** the boundary date (V1 `[Jan 1, Jul 1)`, V2 `[Jul 1, ∞)`).
- Exactly one version resolves per (entity, date); ambiguity is a governance failure
  and raises rather than defaulting.

## Evidence

Every change appends to `epm_audit_event` via `MasterDataAudit` (actor, reason,
old/new values, UTC time) and is immutable under the Task 5 guards.

## Business-user maintenance outlook (F-045)

Effective-dated mapping tables (including chart of accounts), accounts, and group
mappings gain a dedicated visual studio in R4 (Task 57, `mapping-studio.md`): the
same propose/compare/approve/activate/roll-back semantics above, driven from
searchable grids and timeline views, with no vendor-side code changes and no loss of
history. This runbook remains the semantic contract the studio implements.
