# Legacy Retirement Register

Retirement of any legacy treasury source/system is a **separate, explicitly approved
human decision** taken after cutover and a stable parallel/monitoring interval —
never a consequence of technical go-live (Task 38 requirement).

| Column | Meaning |
|---|---|
| `legacy_system` | The legacy source/system under consideration |
| `replaced_by` | The platform contract/wave that took over its scope |
| `parallel_run_evidence` | Reference to the reconciliation evidence during the interval |
| `stability_interval` | The agreed post-cutover monitoring window actually elapsed |
| `retirement_decision` | RETIRE / RETAIN (with rationale) — a named-human act |
| `decided_by` / `decided_at` | The accountable owner and UTC decision time |
| `archive_ref` | The immutable archive holding its historical evidence |
| `rollback_implication` | What retirement means for rollback (fallback window closes) |

## Rules

1. No retirement entry may be created before the R2 GO decision is signed.
2. Each row requires its own decision, rationale, and archive reference; bulk
   "retire everything" approvals are invalid.
3. Once retired, the fallback path for that scope closes — the decision must record
   that acceptance explicitly.
4. Historical evidence is retained per the retention policy regardless of retirement.

## Register (empty until an approved GO exists)

| Legacy system | Replaced by | Parallel evidence | Stability interval | Decision | By / At | Archive | Rollback |
|---|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — | — |
