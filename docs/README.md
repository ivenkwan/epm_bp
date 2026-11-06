# EPM Finance Platform — Documentation Index

Entry point for all platform documentation. Start here; every other doc is reachable
from this page. The authoritative implementation plan is `../todo.md`; the product
requirements are `AI_Empowered_Financial_Management_PRD.md` (this directory).

## Start here

| Document | What it gives you |
|---|---|
| [Developer guide](../developer-guide.md) | Repo layout, prerequisites, build/test/verify commands, quality and operations gates |
| [System overview](architecture/system-overview.md) | Container diagram, service responsibilities, request/data flow, locked technology stack |
| [API reference](api/API_REFERENCE.md) | Every HTTP endpoint with examples, conventions, error model |
| [Data model](data-model.md) | All PostgreSQL tables (V001–V037) grouped by domain, with their owning migration |

## Architecture deep dives (`architecture/`)

| Document | Domain |
|---|---|
| `api-conventions.md` | `/api/v1` non-negotiables: idempotency, ETags, problem+json, correlation |
| `semantic-model.md` | Certified KPI catalog and semantic expressions |
| `cash-calculation.md` / `cash-forecast-model.md` | Certified cash position; versioned rolling forecast |
| `source-contract-schema.md` | Authoritative source register and contracts |
| `manual-adjustment-ledger.md` | Four-eyes adjustments and reversals |
| `migration-control-model.md` | Register controls, cutoff, replay identity, fallback |
| `management-report-model.md` | Governed packs, immutable editions, approval chronology |
| `integrated-planning-model.md` | Non-driver schedules → linked P&L/BS/CF (`icstm-1.0.0`) |
| `variance-and-kpi-planning.md` | Actual/budget variance, shared formula versions |
| `driver-scenario-model.md` | Approved drivers, isolated what-if scenarios |
| `management-only-eliminations.md` | Counterparty matching, MANAGEMENT_ONLY boundary |
| `ai-authority-boundary.md` | What AI may never do; enforcement points |
| `mapping-studio.md` | Business-owned effective-dated mapping maintenance |

## Operations (`operations/`)

Build status per release (`r0`–`r4-build-status.md`), sealed evidence packs
(`r0`–`r4-evidence-pack.md`), gate decisions (`g1-scale-decision.md`,
`r2-go-no-go-decision.md`, `final-acceptance.md`), onboarding and parallel-run
registers, UX acceptance, adoption report, `legacy-retirement-register.md`, and
`requirements-traceability.csv` (216 requirement IDs, zero unmapped).

## Runbooks (`runbooks/`)

Operational procedures for every governed flow — daily treasury operations,
publication/revocation, governed distribution, exception cases, break-glass,
access recertification, historical migration and cutover, backlog recovery,
plan-version reopening, planning cycle and cutover, allocation reversal,
mapping-studio changes, rule/prompt promotion, AI disablement, production incident
command. Each names its invariants, steps, and failure handling.

## Decisions (`decisions/`)

`ADR-001-platform-boundaries.md` (PostgreSQL authority, AGE projection-only,
Drools/DMN and Kogito boundaries, F-043/F-044 exclusions) and
`version-matrix.md` (the locked, digest-pinned technology stack).

## Related top-level artifacts

| Path | Content |
|---|---|
| `todo.md` | The 57-task implementation plan with per-task results |
| `requirements-traceability.csv` (in `operations/`) | Requirement → task → evidence mapping |
| `rules/`, `processes/` | Governed DMN decision tables and BPMN definitions (each doc-referenced, living-documentation-gated) |
| `db/fixtures/` | Golden synthetic fixtures; `expected/README.md` carries the hand calculations every calculator must reproduce |
