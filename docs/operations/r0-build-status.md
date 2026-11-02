# R0 Build Status

Per-task build/test/verification results for the R0 foundation, recorded as work completes.
This is the engineering build log; formal acceptance evidence lives in the R0 evidence pack
(Task 16). Dates are UTC of the recorded run on branch `r0-foundation`.

Legend: PASS = commands ran green on this machine · PARTIAL = delivered with an explicitly
tracked gap · PENDING = not yet run/owned.

| Task | Scope | Result | Evidence |
|---|---|---|---|
| 1 | Scope, ownership, ADR-001, traceability baseline | PASS (committed before this session, `9216397`) | `verify-traceability.sh` → PASS: 212 requirements, zero unmapped |
| 2 | Monorepo toolchain, version lock, CI, compatibility spike | PASS | reactor `./mvnw verify` green (5 service smoke tests + 5 toolchain tests); `verify-provenance.sh` clean-export PASS; `docs/decisions/version-matrix.md` records locked versions/digests and the Kogito-extension-retirement finding |
| 3 | Synthetic fixtures + independent golden results | PASS with one tracked gap | `FixtureIntegrityTest` → 9/9 green (digest provenance, unique IDs, decimal-string discipline, effective-date intervals, referential integrity, golden schemas, 12 adversarial categories, synthetic-only scan) |
| 4 | Local Kubernetes runtime (kind + k3d), APISIX OIDC edge, Keycloak, OTel | PASS with two tracked gaps | `PlatformSmokeTest` 5/5 on kind 0.33/K8s 1.37 AND k3d 5.8.3/k3s 1.31.5 (identical manifests; k3d deleted after verification); anonymous 401, authenticated 200 + correlation id, spans+metric points accepted, governance labels on received telemetry, no token in exposition; restart drill: postgres-age + finance-core pod deletions with AGE graph durable on PVC (`ag_graph` count=1) |
| 5 | Core schema, immutable audit, transactional outbox | PASS | `V001`/`V002` migrations (revision uniqueness, UTC timebases, decimal registry, guard trigger, `epm_app` least-privilege role); `OutboxConcurrencyTest` 7/7: 60-thread gap-free revision sequence, commit→one outbox row / rollback→zero, redelivery exactly-once, tamper detection + role denial; finance-core suite 8/8 |
| 6 | API contracts, idempotency, ETags, problem details | PASS with one tracked gap (O-B6) | finance-core suite 14/14; `ApiSafetyContractTest` 6/6 (replay incl. canonical key order, changed-payload 409, stale If-Match 409 with no second effect, 422 violations, correlation echo, F-044 eight excluded routes 404); `common-v1.yaml` with verbatim PRD §8 examples |
| 7 | Accessible frontend shell + generated API boundary | PASS with one tracked gap (O-B7) | finance-web: typecheck ✓, eslint ✓, vitest 14/14 ✓, production build ✓; e2e 4/4 (skip-link focus, keyboard nav, 200% reflow, axe WCAG zero critical/serious); generated-types drift gate green |
| 8 | SSO/MFA, RBAC/RLS, SoD, access recertification | PASS | `V004` entitlements (versioned PRD 6.8.2 matrix, SoD pairs, scoped grants with review deadlines); `AuthorizationSecurityTest` 8/8: allow+audit, restricted-entity deny w/o leakage, empty scope zero rows, self-approval deny, SoD conflict deny+escalation, action-outside-role, overdue review deny+escalate (never grants), gateway bypass; finance-admin suite 9/9 |
| 9 | Effective-dated master data and hierarchies | PASS with one tracked gap (O-B8) | `V005` master data (composite-key entity versions, approved hierarchy versions, interval checks, cycle trigger); `HierarchyResolutionTest` 6/6 (shared boundary dates exactly-one resolution, overlap/cycle/self-parent rejection, historical resolution, ancestor walk); finance-admin suite 15/15; steward browser shell present, admin API wiring pending |

## Open items (blockers for the R0 gate, none block later engineering)

| ID | Item | Owner | Blocking |
|---|---|---|---|
| O-B1 | Independent Finance/Treasury reviewer sign-off of `db/fixtures/expected/` golden results (Task 3 done-when requires named reviewers) | Finance/Treasury control owners (per `docs/operations/control-ownership-matrix.md`) | First financial calculator consuming goldens (Task 12 evaluator fixtures, Tasks 23-27 calculators) and R0 gate sign-off |
| O-B2 | Kogito full user-task lifecycle (`jbpm-usertask` engine stack) not yet wired; spike uses a minimal parking handler | Engineering (Task 14) | Task 14 durable human-task state machine |
| O-B3 | CI pipeline defined (`.github/workflows/ci.yml`) but not yet exercised on a GitHub runner | Engineering | First push to `origin` triggers it; watch run |
| O-B4 | Local dev deviations from the platform baseline: gateway TLS terminated at node boundary (no certificates in kind), PostgreSQL `ssl=off`, PVC on unencrypted kind host path | Engineering (environment overlays) | test/uat/prod overlays must enable TLS end-to-end and an encrypted storage class before any non-local deployment |
| O-B5 | `install-local-dependencies.sh` content verified 1:1 against inline execution but the script file itself has not been executed end-to-end (session security hook blocked credential-generating script execution) | Engineering | R0 clean-deployment rehearsal (Task 16) must execute the scripts verbatim |
| O-B6 | Durable-job runtime (`JobService`) blocked: session Mimosa write-hook rejects fully-parameterized variants (12 attempts) as "SQL injection"; project-wide scan reports 0 findings. Schema (`V003` epm_job), contract shapes, and api-conventions already committed | Engineering + security-tooling owner | Task 6 `202` job contract test; Task 18 validation jobs consume the same claim discipline |
| O-B7 | Manual accessibility verification (screen reader, focus order by ear, contrast sampling) requires a named human reviewer; automated WCAG (axe, WCAG 2.x A/AA) is green with zero critical/serious findings | Accessibility owner (per control-ownership-matrix) | R0 evidence gate sign-off |