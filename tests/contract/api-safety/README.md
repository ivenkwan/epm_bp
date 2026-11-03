# API-safety contract suite

Executable location: `services/finance-core/src/test/java/dev/epm/core/api/ApiSafetyContractTest.java`
(a black-box HTTP suite; it lives with the finance-core bootstrapping so `@QuarkusTest`
can start the application archive, and moves to a dedicated runner module when services
are deployed separately).

Covered contracts (from `docs/architecture/api-conventions.md`):

- same `Idempotency-Key` + same canonical payload (any key order) → original response,
  `Idempotency-Replayed: true`
- same key + changed payload → `409` `IDEMPOTENCY_KEY_REUSED` (problem+json)
- stale `If-Match` → `409` `STALE_REVISION`, and no extra revision is created
- validation failure → `422` `VALIDATION_FAILED` with field violations
- `X-Correlation-Id` propagation and echo, including on problem responses
- F-044 exclusion guard: no routes exist for `sql`, `cypher`, `drl`, `dmn-exec`, `bpmn`,
  `payments`, `gl-postings`, `autonomous-approvals` (404 problem+json)

Deferred behind blocker O-B6 (see `docs/operations/r0-build-status.md`): the `202` durable
job contract test executes once the job runtime lands.
