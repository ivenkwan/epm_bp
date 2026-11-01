# ADR-001: Platform Responsibility and Authority Boundaries

| Field | Value |
|---|---|
| Status | Proposed |
| Date | 2026-11-01 |
| Deciders | Product, Finance, Treasury, FP&A, Security, Operations, Technical, and Assurance owners (`PENDING_ASSIGNMENT`) |
| Related requirements | F-040; F-042 planning; NFR-018; NFR-023; NFR-028; F-043/F-044 exclusions |

## Context

The platform will integrate heterogeneous financial sources, deterministic calculations, durable human workflows, accessible browser experiences, and optional evidence-grounded AI. These capabilities have different consistency and authority needs. A shared database, gateway, rule engine, process engine, or model must not blur ownership of financial facts or permit autonomous financial action.

The architecture must remain cloud-portable, reproducible, secure when edge or browser controls are bypassed, and operable with AI disabled. Exact technology versions and image digests are deliberately deferred to the Task 2 compatibility and version-locking decision.

## Decision

### 1. PostgreSQL is the sole financial system of record

PostgreSQL owns financial facts, source revisions, mappings, effective-dated versions, adjustments, certification, publication references, process correlation, cases, and transactional outbox records. Authoritative Java transactions enforce domain invariants, idempotency, optimistic concurrency, entitlements, segregation of duties, and audit evidence. Business state and its outbox record commit atomically.

No event payload, browser store, graph relationship, rule-session object, process variable, or AI snapshot becomes an independently editable financial master.

### 2. Apache AGE is a rebuildable projection only

Apache AGE runs inside PostgreSQL and contains stable-ID, provenance-bearing, watermark-aware projections for hierarchy, lineage, intercompany, and impact analysis. Projection builders consume authoritative relational changes idempotently. AGE exposes read-only queries, records relational watermarks and build provenance, and can be deleted and rebuilt without changing financial evidence.

No independent graph mutation API is permitted. Stale required projections block dependent certification rather than supplying apparently current lineage.

### 3. Java/Quarkus deployables own authoritative application behavior

A small number of cohesive Java/Quarkus deployables covers integration, treasury/control, planning, reporting/semantics, and administration. Modules split only for demonstrated scaling, fault-isolation, security, availability, or ownership needs. Java remains authoritative even when APISIX or frontend checks are bypassed.

Public APIs use `/api/v1`, typed contracts, fixed-precision decimal strings with explicit currency/scale/rounding/unit, UTC `recorded_at`, distinct business time fields, idempotency keys, ETags, durable jobs, and safe `application/problem+json` errors. Binary floating point is forbidden for authoritative money, rates, and covenant calculations.

### 4. React/Vite and strict TypeScript own interaction, not truth

The web application uses React/Vite, generated API types, runtime boundary validation, accessible interaction, locale-preserving formatting, and user-intent capture. It does not own authoritative calculations, entitlements, secrets, certification, workflow state, or direct database access. Authoritative decimal values remain strings until display; unreviewed `any` is prohibited.

### 5. APISIX protects only the edge

APISIX owns TLS termination, OIDC integration, routing, request/rate limits, correlation propagation, and canary controls. It does not own entity/version/object entitlements, segregation of duties, financial validation, certification, publication, or workflow state. Backend denial tests must still pass when gateway filters are bypassed in controlled tests.

### 6. Drools and DMN provide side-effect-free decisions

Drools and DMN provide typed, versioned, explainable, bounded, and terminating validation, inference, calculations, tolerances, and approval-level selection. Every rule/decision row has independent expected-result and boundary coverage. Backward goals have cycle/depth limits, and rule order cannot change authoritative results.

Rules cannot mutate databases, advance workflows, publish outputs, deploy assets, post to a GL, initiate payments, apply adjustments, or approve/certify work. Java commits authoritative results; Kogito coordinates any required human work.

### 7. Kogito coordinates durable human work

Kogito owns durable next action, actor assignment, human tasks, timers, escalation, compensation, and process correlation. Persisted process state is not a financial fact source. Java owns the corresponding business-object transaction and state.

Timers may remind, reassign, escalate, or change visibility only. They never approve, close, certify, publish, post, pay, or apply adjustments. Case closure and business-object certification remain separate human decisions.

### 8. AI has no autonomous financial authority

AI is limited to Tier 0 deterministic behavior, Tier 1 drafts, and Tier 2 recommendations from certified, access-filtered snapshots. Numeric claims must exactly match cited evidence and unsupported claims are suppressed or marked invalid. Deterministic operation remains available with AI disabled.

F-043 is excluded: the platform must not replace or claim statutory consolidation or statutory reporting; management-only eliminations must be labeled accordingly. F-044 is excluded: no AI, timer, rule, process, API, or privileged session may autonomously initiate payments, post to a GL, apply adjustments, change access, certify, approve, publish, or deploy. Attempts are denied and logged through the owning controls.

## Alternatives considered

- **A single application/database boundary with implicit responsibilities:** rejected because hidden coupling would make consistency, security, and independent verification harder.
- **AGE as a second curated master:** rejected because independent graph edits would destroy relational authority and reproducibility.
- **Gateway or frontend enforcement as the primary security boundary:** rejected because bypasses would expose restricted data and controlled actions.
- **Rules or BPMN directly committing financial effects:** rejected because side effects would weaken explanation, replay, recovery, and human authority.
- **Microservices per business object or autonomous AI execution:** rejected as unnecessary operational complexity and a direct violation of the authority model.

## Consequences

### Benefits

- One authoritative relational basis supports reconciliation, immutable evidence, replay safety, and independent acceptance.
- Graph, rules, processes, frontend, gateway, and AI can be tested against explicit anti-responsibilities.
- Deterministic operation and manual fallback remain available when optional capabilities are disabled.
- Cloud portability and rolling compatibility remain separate from financial semantics.

### Costs and obligations

- Projection watermarks, outbox deduplication, rebuilds, process recovery, contract tests, and security denial suites are mandatory engineering work.
- Fixed-precision calculations, versioned rules/processes, runtime API validation, and independent expected results require ongoing discipline.
- Every release must update traceability, tests, runbooks, and documentation together; a screen or generated test alone is insufficient evidence.

## Compliance and verification

- `docs/operations/requirements-traceability.csv` maps each requirement to owning tasks and intended evidence.
- `scripts/quality/verify-traceability.sh` blocks missing, duplicate, orphaned, unmapped, or evidence-less requirement rows.
- Tasks 6, 14, 29, 30, 51, and 56 provide F-044 negative API, workflow, adjustment, privileged-access, AI/timer, and final acceptance tests.
- Tasks 50 and 56 provide F-043 statutory-representation denial and management-only labeling tests.
- Release evidence must demonstrate clean deployment, recovery, security, accessibility, financial reconciliation, human approval, and current documentation without treating proposed targets as achieved.

This ADR remains **Proposed** until the named deciders record acceptance. It does not itself authorize business go-live, production deployment, or a release-gate transition.
