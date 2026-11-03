# R0 Evidence Pack

Release: **R0 — Foundation and Control Baseline**
Tag/commit base: `r0-foundation` branch HEAD at rehearsal time.
Rehearsal date (UTC): 2026-11-03. This pack records what was executed and verified;
unverified claims are absent by construction.

## Regression evidence

| Suite | Result |
|---|---|
| Maven reactor `./mvnw verify` (6 modules) | BUILD SUCCESS — 91 tests, 0 failures, 0 errors; 5 platform-smoke tests skipped (cluster-gated by design, executed separately below) |
| finance-core | 14/14 (outbox concurrency 7, API-safety contracts 6, smoke 1) |
| finance-integration | 13/13 (source contracts 8, landing immutability 4, smoke 1) |
| finance-admin | 25/25 (authorization security 8, case recovery 10, hierarchy resolution 6, smoke 1) |
| finance-reporting | 8/8 (KPI evaluation 7, smoke 1) |
| finance-planning | 1/1 (smoke) |
| tests/integration | 30 tests: toolchain compatibility 5/5, rule governance 6/6, fixture integrity 9/9, design contracts 5/5, platform smoke 5/5 (with `EPM_PLATFORM_SMOKE=true`) |
| finance-web | typecheck ✓, eslint ✓, vitest 14/14 ✓, production build ✓ |
| e2e accessibility (Playwright + axe) | 4/4 (skip-link focus, keyboard navigation, 200% reflow, WCAG 2.x A/AA scan zero critical/serious) |

## Quality gates

| Gate | Result |
|---|---|
| `verify-traceability.sh` | PASS — 212 requirements traced, zero unmapped |
| `verify-rules.sh` | PASS — assets well-formed (XML validated), governance suite 6/6 |
| `verify-api-types.sh` | PASS — generated types match `common-v1.yaml` |
| `verify-provenance.sh --quick` | PASS — clean export builds with committed assets only |
| CycloneDX aggregate SBOM | Generated at package in every build (`target/*.json` per module; CI publishes) |

## Platform evidence

Deployed on kind 0.33 (Kubernetes 1.37) from committed manifests; previously verified
identical on k3d 5.8.3/k3s 1.31.5 (Task 4, cluster deleted after verification).

- All pods Running: apisix, keycloak, otel-collector, finance-core, postgres-age (+ AGE
  init job Completed).
- Flyway migrated the empty database at deployment: V001→V009 applied, 33 public tables
  present in the cluster database.
- Gateway control: anonymous → 401; token-authenticated → 200 + `X-Correlation-Id`;
  rate/body limits and JWKS bearer validation at the edge.
- Telemetry: correlated OTLP spans + metric points accepted by the collector; received
  metrics carry `epm_environment="dev"`, `epm_product`, `epm_cost_center`; raw bearer
  tokens absent from telemetry exposition; authorization/cookie attributes redacted.
- F-043/F-044 exclusions (live gateway, authenticated): `sql`, `cypher`, `drl`, `bpmn`,
  `payments`, `gl-postings`, `autonomous-approvals` → 404 (no route exists).

## Recovery rehearsal

- Service restart drill: finance-core + otel-collector pods deleted and recovered
  (deployment available); postgres pod deleted and recovered; AGE graph `epm_lineage`
  survived on the PVC (`ag_graph` count = 1 after restart).
- PostgreSQL backup/restore smoke: `pg_dump -s -n public` from the cluster database
  (33 tables) restored into a scratch container — 33/33 tables recreated.
- AGE rebuild path: destroy-and-rebuild from relational watermarks is the designed
  discipline (Task 21 implements it; the restart drill proves the graph persists and the
  init job is idempotent).
- Rollback/roll-forward: dev overlay is declarative; redeployment from committed
  manifests is the rehearsed path (executed multiple times during R0).

## Exclusions and boundaries (verified as designed)

- No business go-live claim: R0 is foundation only.
- F-043/F-044 excluded behavior has no routes and is covered by negative contract tests
  plus the live-gateway 404 checks above.
- Management-only eliminations / statutory consolidation: absent by design.

## Open items (blockers carried to the gate decision)

See `docs/operations/r0-build-status.md` § Open items: O-B1 (fixture golden + control
design sign-offs by named owners), O-B2/O-B6 (Kogito user-task runtime and durable-job
runtime blocked by the session security hook — fully-parameterized code, project-wide
scan clean), O-B4 (dev TLS/encrypted-storage deviations), O-B5 (setup-script verbatim
execution), O-B7 (manual accessibility verification), O-B8 (admin browser wiring).

## Sign-offs

- [ ] Product owner — R0 scope accepted
- [ ] Finance/Treasury control owner — fixtures, KPI cards, control designs (O-B1)
- [ ] Security owner — identity/SoD matrix, exclusions, open blockers disposition
- [ ] Operations owner — platform, recovery rehearsal, runbooks
- [ ] Assurance owner — evidence completeness and traceability

R1 is authorized only when the sign-offs above are recorded and the open blockers
dispositioned (accept-with-rationale or remediated).
