# Mapping Studio (F-045, planned Task 57)

The Mapping studio is the business-user surface for maintaining **effective-dated
mappings as governed configuration**: mapping tables (including the chart of
accounts), accounts, and group mappings. It is planned for R4 (Task 57) on top of the
R0 effective-dated master-data foundation (Task 9, `V005__master_data.sql`,
`MasterDataService`).

## Product contract

| Dimension | Contract |
|---|---|
| Who | Entitled data stewards (Group Finance/EPM); every change independently approved and activated — never vendor-side code changes |
| What | Mapping tables / chart of accounts, accounts, group memberships; each row carries effective-from/effective-to, provenance (actor, reason, UTC), and version lineage |
| How (visual) | Searchable mapping grids, timeline views of effective dating, visual hierarchy/group assignment, side-by-side old/new compare, propose → approve → activate → roll back |
| History | **No loss of history**: every change (including rollback) appends a versioned row with old/new evidence via the immutable audit trail; nothing is deleted or overwritten; historical reports resolve only to the version effective at their date |
| Guardrails | Overlapping/gapped effective dates, duplicate stable IDs, group-integrity violations, and unauthorized edits are refused with actionable errors, leaving active mappings untouched |

## Design intent

1. **Configuration, not code.** A mapping change is a governed data change — no
   deployable artifact, rule file, or vendor involvement changes. This is the
   structural guarantee behind "without vendor-side code changes".
2. **Effective-dated by construction.** The studio edits the same effective-dated
   structures the platform resolves against (Task 9 semantics: from-inclusive /
   to-exclusive, exactly-one resolution per date); adding a mapping never rewrites an
   interval that historical products already resolved.
3. **Visual and intuitive.** Timeline-first editing so stewards *see* adjacency before
   submitting; old/new compare before activation; mass changes preview their impact
   before approval.
4. **Human-gated activation.** Propose/approve separation with independent approval;
   rollback is a new version that supersedes forward — history is never pruned.
5. **Responsive and accessible.** The studio is part of the governed browser
   experience: WCAG-scanned, keyboard-operable, and usable at 200% zoom.

## Traceability

- PRD: feature catalogue row F-045 (Must), acceptance criteria AC-F-045-01–03, US-004,
  dependency edge `F-001 → F-045`, R4 release scope.
- Plan: Task 57 (todo.md) with dependencies on Tasks 8 (authorization), 9 (effective
  dating), 14 (cases), 47 (responsive experience); Task 56 final gate covers
  F-001–F-045.
- Register: `docs/operations/requirements-traceability.csv` rows F-045 and
  AC-F-045-01–03 (216 traced IDs total).
