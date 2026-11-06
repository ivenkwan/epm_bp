# Developer Guide

Everything needed to build, test, and verify the platform locally. The locked tool
versions live in `decisions/version-matrix.md` — do not substitute.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | Temurin 21 (`[21,22)` enforced) | enforcer-checked on every build |
| Maven | 3.9.11 via wrapper | always `./mvnw`, never a system Maven |
| Node.js + pnpm | 22 LTS / pnpm 10 | frontend workspace and e2e |
| Docker | API ≥ 1.44 | Testcontainers (`apache/age:release_PG17_1.7.0`) |
| Kubernetes (local) | kind | `scripts/setup/` provisions cluster, APISIX, Keycloak, OTel |

## Repository layout

| Path | Content |
|---|---|
| `services/finance-core` | Revisions, audit, outbox, idempotency, certification, treasury calculators, adjustment controls |
| `services/finance-integration` | Contracts, landing, validation, ingestion jobs, migration runner |
| `services/finance-planning` | Dimensions/FX, plan versions, statements, calendar, variance, scenarios, allocations, eliminations |
| `services/finance-reporting` | KPI catalog, packs, dashboards, exports, commentary, AI control plane |
| `services/finance-admin` | Authorization, identity, master data, cases, promotions, mapping studio |
| `apps/finance-web` | React frontend (strict TS, zod-validated API client, generated types) |
| `tests/integration` | Toolchain, contract, recovery, process, migration, security suites |
| `tests/performance` | Capacity/failure gates and final workloads |
| `tests/e2e` | Playwright + axe journeys (shell, reporting, planning, treasury, accessibility) |
| `db/migrations` | Flyway V001–V037 (source of truth for the schema) |
| `db/fixtures` | Golden synthetic fixtures + hand-calculated expectations |
| `rules/`, `processes/` | DMN decision tables, BPMN definitions (living-documentation-gated) |
| `scripts/quality`, `scripts/operations` | Release/quality gates, operational drills |
| `config/environments` | Non-secret environment configuration; secrets referenced by name only |

## Build and test

```bash
# Full reactor (7 modules, ~2.5 min): unit + Quarkus component tests + gates
./mvnw verify

# One module
./mvnw -pl services/finance-planning test

# Frontend (from apps/finance-web)
pnpm install && pnpm exec tsc --noEmit && pnpm exec eslint . && pnpm exec vitest run && pnpm exec vite build

# E2E (needs `vite preview` on :5173; from tests/e2e)
pnpm exec playwright test

# Platform smoke (needs the kind cluster up)
EPM_PLATFORM_SMOKE=true ./mvnw -pl tests/integration -Dtest='PlatformSmokeTest' test
```

Service tests spin ephemeral containers (`apache/age:release_PG17_1.7.0`) and run
Flyway from `db/migrations` — no shared database state, no manual setup.

## Quality gates (all must PASS before a release)

| Gate | Command | Checks |
|---|---|---|
| Traceability | `scripts/quality/verify-traceability.sh` | 216 requirement IDs mapped, zero unmapped |
| API types | `scripts/quality/verify-api-types.sh` | Generated TS types match `common-v1.yaml` |
| Rules | `scripts/quality/verify-rules.sh` | DMN/DRL assets compile and agree with services |
| Provenance | `scripts/quality/verify-provenance.sh` | Clean export builds; every governed asset doc-referenced |
| Security | `scripts/quality/verify-security.sh [--suite ai]` | Dependency/secret scans; AI registry ships disabled, no credential literals |
| Migration | `scripts/operations/verify-migration.sh` | Migration controls (totals, tolerance, cutoff, replay) |
| Recovery | `scripts/operations/verify-r1-recovery.sh` | AGE rebuild, outbox replay, certification restore |
| Cutover | `scripts/operations/cutover-treasury.sh verify` | Treasury cutover phases |
| Planning migration | `scripts/operations/run-planning-migration.sh --verify` | Planning object-class controls |
| Final recovery | `scripts/operations/verify-final-recovery.sh` | Chains every recovery gate + adversarial + final workloads |
| Release | `scripts/quality/verify-release.sh --release R{0..4}` | Evidence pack sections, build status rows, sub-gates |

## Conventions every change must keep

1. **Money is decimal strings** with explicit currency/scale/rounding — never JSON
   numbers, never floating point.
2. **Mutations are idempotent** (`Idempotency-Key` bound to actor + canonical
   payload) and **revision-append-only** (`ETag: "revision-N"`, `If-Match` guards).
3. **Fail closed**: authorization denials carry a safe reason; uncertified scope
   never enters certified products; null is never coerced to zero.
4. **Golden fixtures are the contract**: `db/fixtures/expected/` hand calculations
   must be reproduced exactly by calculators (scale 2, HALF_UP, decimal strings).
5. **Credentials come from the environment only** — never literals in source,
   examples, or tests.
6. **Governed behavior changes require a doc delta** (living-documentation gate) and
   tests that agree with the DMN/BPMN policy assets.

## Where to look next

- Architecture per domain: `docs/architecture/` · HTTP surface: `docs/api/API_REFERENCE.md`
- Schema: `docs/data-model.md` · Operational procedures: `docs/runbooks/`
- The plan with per-task results: `todo.md` · Requirement→evidence mapping:
  `docs/operations/requirements-traceability.csv`
