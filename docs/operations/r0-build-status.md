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

## Open items (blockers for the R0 gate, none block later engineering)

| ID | Item | Owner | Blocking |
|---|---|---|---|
| O-B1 | Independent Finance/Treasury reviewer sign-off of `db/fixtures/expected/` golden results (Task 3 done-when requires named reviewers) | Finance/Treasury control owners (per `docs/operations/control-ownership-matrix.md`) | First financial calculator consuming goldens (Task 12 evaluator fixtures, Tasks 23-27 calculators) and R0 gate sign-off |
| O-B2 | Kogito full user-task lifecycle (`jbpm-usertask` engine stack) not yet wired; spike uses a minimal parking handler | Engineering (Task 14) | Task 14 durable human-task state machine |
| O-B3 | CI pipeline defined (`.github/workflows/ci.yml`) but not yet exercised on a GitHub runner | Engineering | First push to `origin` triggers it; watch run |
| O-B4 | Local dev deviations from the platform baseline: gateway TLS terminated at node boundary (no certificates in kind), PostgreSQL `ssl=off`, PVC on unencrypted kind host path | Engineering (environment overlays) | test/uat/prod overlays must enable TLS end-to-end and an encrypted storage class before any non-local deployment |
| O-B5 | `install-local-dependencies.sh` content verified 1:1 against inline execution but the script file itself has not been executed end-to-end (session security hook blocked credential-generating script execution) | Engineering | R0 clean-deployment rehearsal (Task 16) must execute the scripts verbatim |
