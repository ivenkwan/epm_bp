# API Conventions (`/api/v1`)

Single source of truth for the shared component shapes: `services/finance-core/src/main/openapi/common-v1.yaml`.

## Non-negotiables

1. **Money and rates are strings** (`DecimalString`) with explicit `CurrencyCode`, scale, and rounding metadata. A JSON number is never acceptable for an authoritative amount.
2. **Mutations require `Idempotency-Key`**, bound server-side to (actor, operation, canonical payload fingerprint). The canonical fingerprint sorts JSON object keys recursively, so the same payload in a different key order replays; a different payload under the same key is `409 IDEMPOTENCY_KEY_REUSED`. The idempotency row commits **in the same transaction** as the business effect.
3. **Revisions and ETags**: every governed resource exposes `ETag: "revision-N"` derived from the append-only revision. Updates require `If-Match`; a stale value returns `409 STALE_REVISION` without creating any effect.
4. **Long work returns `202` + job resource** (`JobResource`): durable rows with QUEUED/RUNNING/SUCCEEDED/FAILED, progress, and exactly-once execution (unique type+digest, claim-guarded execution counter). *(R0 status: the durable-job runtime is blocked by security-hook false positives — O-B6 — the contract shape above is authoritative.)*
5. **Errors are `application/problem+json`** (`Problem`): stable `code`, human-safe `detail`, `correlationId`, and field-level `violations`. Stack traces and internals never appear; unmapped failures return a generic 500 problem.
6. **Correlation**: `X-Correlation-Id` is honored or generated, propagated to responses, audit rows, outbox events, and telemetry. Example ids: `corr-20261031-000123`.
7. **Excluded by design (F-044)**: no routes exist for arbitrary SQL, Cypher, DRL/DMN execution, BPMN upload, payments, GL posting, or autonomous approvals. Unknown routes return `404` as problem+json (asserted by contract tests).
8. **Time bases stay distinct**: `recordedAt` is receiving time in UTC (RFC-3339, Z); `effectiveDate` is the business date; never coerce one into the other.

## Response status map

| Situation | Status | Problem code |
|---|---|---|
| Created (first submission) | 201 | — |
| Idempotent replay | original status + `Idempotency-Replayed: true` | — |
| Long work accepted | 202 + job resource | — |
| Validation failure | 422 | `VALIDATION_FAILED` (+violations) |
| Same key, different payload | 409 | `IDEMPOTENCY_KEY_REUSED` |
| Stale If-Match | 409 | `STALE_REVISION` |
| Missing Idempotency-Key / If-Match | 400 | `MISSING_IDEMPOTENCY_KEY` / `MISSING_IF_MATCH` |
| Unknown resource/route | 404 | `SUBMISSION_NOT_FOUND` / `HTTP_404` |
| Unmapped failure | 500 | `INTERNAL_ERROR` (no internals) |

## Actor identity in R0

The `X-Actor` header stands in for the authenticated principal until Task 8 replaces it with the OIDC identity propagated by the gateway (`userinfo` header). Server-side enforcement never trusts the browser; the gateway header is only a transport for the identity validated at the edge.
