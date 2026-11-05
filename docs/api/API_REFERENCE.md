# EPM Finance Platform — API Reference

Governing contract components: `services/finance-core/src/main/openapi/common-v1.yaml`
(OpenAPI 3.1). Convention rules: `docs/architecture/api-conventions.md`. This
reference catalogs every HTTP surface in the code base with working examples;
generated TypeScript types live in `apps/finance-web/src/api/generated/common-v1.ts`
(drift-gated by `scripts/quality/verify-api-types.sh`).

## 1. Services and base URL

| Service | Root path | Artifact | Role |
|---|---|---|---|
| finance-core | `/api/v1/core` | `services/finance-core` | API safety, revisions, audit, outbox, certification, treasury calculators |
| finance-integration | `/api/v1/integration` | `services/finance-integration` | Source contracts, landing, validation, ingestion jobs |
| finance-planning | `/api/v1/planning` | `services/finance-planning` | Dimensions/FX, plan versions, statements, calendar, variance |
| finance-reporting | `/api/v1/reporting` | `services/finance-reporting` | KPI catalog, packs, dashboards, exports, AI control plane |
| finance-admin | `/api/v1/admin` | `services/finance-admin` | Authorization, identity, master data, cases, mapping studio |

All traffic enters through the APISIX gateway (`EPM_GATEWAY_URL`, e.g.
`http://localhost:18080` in the local environment), which terminates OIDC and
propagates identity. Business logic beyond the endpoints listed here is exposed as
**governed internal operations** (§8) — deliberately not HTTP, so no automation can
reach it (F-044 exclusion).

## 2. Authentication and identity

- **Production**: OIDC via Keycloak at the gateway; the validated identity is
  forwarded to services. Clients authenticate with bearer tokens issued by the
  platform realm.
- **Local/R0 compatibility**: `X-Actor` names the acting principal when OIDC is not
  in the path; services treat a missing/blank value as `anonymous`. Server-side
  authorization never trusts the browser — `EntitlementResource` proves enforcement
  survives a gateway bypass.
- **Correlation**: send `X-Correlation-Id` to trace a request end to end; it is
  honored or generated, echoed on every response, and propagated to audit rows,
  outbox events, and telemetry.

## 3. Cross-cutting conventions (non-negotiable)

1. **Money is strings.** Amounts cross the API as fixed-precision decimal strings
   (`"1250000.00"`), never JSON numbers, with explicit currency, scale, and rounding
   metadata where applicable.
2. **Mutations require `Idempotency-Key`** (UUID). The key binds server-side to
   (actor, operation, canonical payload fingerprint). The same key with the same
   payload replays the original response plus `Idempotency-Replayed: true`; the same
   key with a different payload is `409 IDEMPOTENCY_KEY_REUSED`. The idempotency row
   commits in the same transaction as the business effect.
3. **Revisions and ETags.** Governed resources expose `ETag: "revision-N"`. Updates
   require `If-Match` with the current ETag; a stale value is `409 STALE_REVISION`
   and creates no effect.
4. **Errors are `application/problem+json`** with stable `code`, human-safe `detail`,
   `correlationId`, and optional field-level `violations`. Internals never leak;
   unmapped failures return a generic 500 problem.
5. **Time bases stay distinct**: `recordedAt` is receiving time (UTC, RFC-3339 `Z`);
   `effectiveDate` is the business date. Never coerced.

### Status / problem-code map

| Situation | Status | Problem `code` |
|---|---|---|
| Created (first submission) | 201 | — |
| Idempotent replay | original status + `Idempotency-Replayed: true` | — |
| Validation failure | 422 | `VALIDATION_FAILED` (+ `violations[]`) |
| Malformed JSON | 422 | `MALFORMED_JSON` |
| Same key, different payload | 409 | `IDEMPOTENCY_KEY_REUSED` |
| Stale `If-Match` | 409 | `STALE_REVISION` |
| Missing `Idempotency-Key` | 400 | `MISSING_IDEMPOTENCY_KEY` |
| Missing `If-Match` | 400 | `MISSING_IF_MATCH` |
| Unknown resource | 404 | `SUBMISSION_NOT_FOUND` / `HTTP_404` |
| Authorization denied | 403 | — (`{decision, reason}` body) |
| Unmapped failure | 500 | `INTERNAL_ERROR` (no internals) |

## 4. Health endpoints

`GET /api/v1/{core|integration|planning|reporting|admin}/health`

```bash
curl -s "$EPM_GATEWAY_URL/api/v1/planning/health"
```

```json
{ "service": "finance-planning", "release": "R0_TOOLCHAIN", "respondedAtUtc": "2026-11-05T09:00:00Z" }
```

## 5. Submissions (finance-core) — the API-safety contract

Base: `/api/v1/core/submissions`. This surface demonstrates the platform contracts
every governed endpoint inherits: idempotent creation, revision ETags with
conditional updates, and problem+json failures.

### 5.1 Create a submission

`POST /api/v1/core/submissions`

| Header | Required | Notes |
|---|---|---|
| `Idempotency-Key` | yes | UUID; binds to actor + canonical payload |
| `X-Actor` | no | Principal when OIDC is not in path |
| `X-Correlation-Id` | no | Generated when absent |

Request body (JSON):

```json
{ "effectiveDate": "2026-10-31", "currency": "USD", "openingCash": "1250000.00" }
```

Validation rules: `effectiveDate` matches `YYYY-MM-DD` (`PERIOD_INVALID` otherwise),
`currency` matches `[A-Z]{3}` (`CURRENCY_INVALID`), `openingCash` matches
`^-?\d+\.\d{2}$` (`PRECISION_EXCEEDED`). All violations are returned together.

```bash
curl -s -X POST "$EPM_GATEWAY_URL/api/v1/core/submissions" \
  -H "Idempotency-Key: 8fa4c68e-93d4-4ea0-9543-a2ea51090001" \
  -H "X-Actor: preparer@example.invalid" \
  -H "Content-Type: application/json" \
  -d '{"effectiveDate":"2026-10-31","currency":"USD","openingCash":"1250000.00"}'
```

`201 Created`:

```json
{
  "submissionId": "sub-3f2c9a10-5f2e-4b6e-9a1e-7c2d8b0a4e55",
  "revision": 1,
  "status": "RECEIVED",
  "recordedAt": "2026-10-31T01:15:22Z",
  "links": { "self": "/api/v1/core/submissions/sub-3f2c9a10-5f2e-4b6e-9a1e-7c2d8b0a4e55" }
}
```

Replaying the identical request (same key, same payload) returns the original
response with `Idempotency-Replayed: true` and no second revision.

`422` example (`application/problem+json`):

```json
{
  "type": "about:blank",
  "title": "Submission validation failed",
  "status": 422,
  "detail": "Submission validation failed",
  "code": "VALIDATION_FAILED",
  "correlationId": "corr-20261031-000123",
  "violations": [
    { "field": "openingCash", "code": "PRECISION_EXCEEDED", "message": "Use no more than two decimal places." }
  ]
}
```

### 5.2 Read a submission

`GET /api/v1/core/submissions/{submissionId}`

Returns the current revision and the strong `ETag` header (`"revision-N"`); the body
also carries the revision. Unknown ids are `404 SUBMISSION_NOT_FOUND`.

```bash
curl -si "$EPM_GATEWAY_URL/api/v1/core/submissions/sub-3f2c9a10"
```

### 5.3 Update a submission (conditional)

`PUT /api/v1/core/submissions/{submissionId}`

| Header | Required |
|---|---|
| `If-Match` | yes — the current `ETag` |
| `X-Actor` | no |

- Missing `If-Match` → `400 MISSING_IF_MATCH`.
- Stale `If-Match` → `409 STALE_REVISION`, **no second revision created**.
- Match → new revision allocated, audit + outbox event appended, `200 OK` with the
  new `ETag`.

## 6. Administration (finance-admin)

Base: `/api/v1/admin`. Decisions are made server-side from governed tables; these
endpoints are exercised by the security suite to prove enforcement survives a
gateway bypass.

### 6.1 Authorize an action

`POST /api/v1/admin/authorize`

```json
{ "objectType": "adjustment", "action": "APPROVE", "entity": "ENT-101", "preparer": "p@example.invalid" }
```

- Allowed → `200 {"decision": "ALLOW"}`
- Denied → `403 {"decision": "DENY", "reason": "<safe reason>"}` — the reason names
  the governed rule (e.g. self-approval), nothing else.

### 6.2 List entitled entities

`GET /api/v1/admin/entitlements`

Row-level scope for the caller: `200 {"entities": ["ENT-101", "ENT-102"]}`. An empty
scope returns zero rows — never a wildcard.

### 6.3 Open an access review period

`POST /api/v1/admin/access-reviews`

```json
{ "user": "contractor@example.invalid" }
```

`201 Created` → `{ "reviewId": 42 }` (reviewer is the resolved caller).

### 6.4 Decision count

`GET /api/v1/admin/decisions/count?user=<user>`

Security-suite convenience query; currently returns `{ "user": "...", "count": 0 }`.

## 7. Shared schemas (`common-v1.yaml`)

| Schema | Shape |
|---|---|
| `DecimalString` | `^-?\d+\.\d{1,12}$` — money/rates as strings, never JSON numbers |
| `CurrencyCode` | `^[A-Z]{3}$` |
| `DecimalMetadata` | `{ scale: 0–12, roundingMode: HALF_UP | HALF_EVEN | DOWN }` |
| `UtcTimestamp` | RFC-3339 with `Z` (receiving time) |
| `EffectiveDate` | ISO date (business timebase) |
| `RevisionRef` | `{ objectType, stableId, revision }` |
| `ETag` | `"revision-N"` strong tag |
| `CorrelationId` | e.g. `corr-20261031-000123` |
| `JobResource` | `{ jobId, jobType, status: QUEUED|RUNNING|SUCCEEDED|FAILED, submittedAt, progressPercent?, result?, links }` — durable long-work contract (202) |
| `EventEnvelope` | Outbox event: `{ eventId (uuid), aggregateType, aggregateId, eventType, correlationId, occurredAt }` |
| `Problem` | `{ type, title, status, detail?, instance?, code, correlationId, violations[] }` |
| `Violation` | `{ field, code, message }` — human-safe, no internals |
| `SubmissionAck` | `{ submissionId, revision, status: RECEIVED, recordedAt, links }` |

Reusable responses: `AcceptedJob`, `UnprocessableEntity`, `ConflictIdempotencyKeyReuse`,
`ConflictStaleRevision`. Reusable parameters: `IdempotencyKey`, `IfMatch`,
`CorrelationHeader`.

## 8. Excluded by design (F-044)

No routes exist — and none may be added — for arbitrary SQL/Cypher/DRL/DMN
execution, BPMN upload, payments, GL posting, or autonomous approvals. Unknown
routes return `404` as problem+json; contract tests assert the exclusion
(`tests/contract/api-safety/`, live-gateway 404 probes).

## 9. Governed internal operations (not HTTP)

Domain capability beyond the surfaces above lives as versioned service-layer
operations invoked inside the platform only, each documented in
`docs/architecture/`: treasury calculators and certification (finance-core),
contracts/landing/validation/jobs (finance-integration), planning domains
(finance-planning), catalog/packs/dashboards/exports/AI control plane
(finance-reporting), authorization/cases/mapping studio (finance-admin). They carry
the same invariants — idempotency, revision append-only, fail-closed authorization —
enforced at the service boundary without an HTTP attack surface.

## 10. Client integration checklist

1. Generate a UUID `Idempotency-Key` per logical mutation; reuse it on retry.
2. Read `ETag` from GET responses; send it as `If-Match` on updates.
3. Capture `X-Correlation-Id` from responses for support/triage.
4. Treat amounts as strings end to end; never parse to floating point.
5. Handle `application/problem+json` on any non-2xx: branch on `code`, surface
   `violations[].message` to users.
6. The reference frontend client is `apps/finance-web/src/api/client.ts` (zod-
   validated, correlation-aware).
