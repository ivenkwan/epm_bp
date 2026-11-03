# R2 Onboarding Register

Wave-based source/entity onboarding for treasury scale (Task 35). Every wave is a
registered, owned, evidence-gated unit; **unregistered "complete" claims are
rejected** by the onboarding gate (`scripts/operations/onboard-treasury-scope.sh`).

## Register columns (per wave)

| Column | Meaning |
|---|---|
| `wave_id` | Stable wave identifier (`WAVE-<n>`) |
| `sources` / `entities` | Confirmed population in this wave (contracts + entity ids) |
| `owner` | Named accountable owner for the wave |
| `contract` / `template_or_feed` | Authoritative contract id and intake channel |
| `grain` / `cutoff` | Declared grain and business cutoff |
| `control_totals` | Rows + amount per period; parallel outputs must reproduce them |
| `historical_depth` | Months of approved history loading with the wave |
| `entitlement_scope` | Roles/entity scopes provisioned with the wave (Task 8 matrix) |
| `readiness_evidence` | Contract, validation, reconciliation, security, parallel-output checks |
| `rollback_boundary` | The wave is the unit of rollback (deactivate the wave, not the release) |

## Wave status machine

```
REGISTERED → CHECKS_RUNNING → CHECKS_PASSED → ACTIVE
                   ↘ CHECKS_FAILED → REMEDIATE (wave-scoped; release unaffected)
ACTIVE → ROLLED_BACK (wave boundary only)
```

A wave activates **only** when contract, validation, reconciliation, security, and
parallel-output checks pass for its whole population; the next wave cannot start while
one is in REMEDIATE. Failed waves are remediated or returned to the owner — Must
controls are never diluted to admit a wave.

## Capacity/failure gates (delivered as tests)

`tests/performance/treasury/PeakBacklogTest.java` covers, before any scale claim:
concurrent submissions, duplicate/replayed feeds, late/missing sources, validation
quarantine under load, certification contention (one decision per revision), outbox
backlog drainage, and projection (watermark) lag recovery. The signed 2× peak /
return-to-normal workload executes against the confirmed population (owner-gated R2
measurement); synthetic backlog clearance within the proposed 60 minutes is measured,
not claimed, until then.

## Wave log (filled per wave; no entry = not onboarded)

| Wave | Sources/Entities | Owner | Status | Evidence |
|---|---|---|---|---|
| — | — | — | — | awaiting G1 scale decision + OQ-001 cohort |
