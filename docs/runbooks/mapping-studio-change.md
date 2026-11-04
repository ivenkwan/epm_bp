# Runbook: Mapping Studio Change (Task 57 / F-045)

How business users change effective-dated mapping tables (including chart of
accounts), accounts, and group mappings through the visual studio — as governed
configuration, never vendor-side code changes, with no loss of history.

## Change flow

1. **Open the studio** (Planning tab → Mapping studio): mapping versions render on
   effective-dated timelines with status and provenance (proposer/approver).
2. **Propose** — edit rows on the timeline. The studio refuses with actionable
   errors before anything lands:
   - effective dates overlapping an active version (the conflicting interval is
     named);
   - duplicate row keys;
   - group mappings missing their exactly-one governed group;
   - unentitled editors.
3. **Preview impact** — the mass-change preview lists affected keys and counts;
   nothing changes until approval and activation.
4. **Compare** — side-by-side old/new before submitting for approval.
5. **Approve + activate** — an independent approver (never the proposer) activates;
   the version records actor, reason, and UTC provenance.
6. **Roll back** — rollback creates a **new version** superseding forward; the prior
   version stays in history with its old/new evidence. Nothing is deleted.

## Invariants

- Historical requests resolve only to the version effective at their date
  (from-inclusive / to-exclusive).
- Every action appends to `epm_mapping_audit` (PROPOSE/REFUSE/APPROVE/ACTIVATE/
  ROLLBACK with old/new values).
- Mapping changes are data: no deployable artifact, rule file, or vendor code
  changes.

Semantics contract: `docs/runbooks/master-data-change.md` (interval rules) and
`docs/architecture/mapping-studio.md`.
