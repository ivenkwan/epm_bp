# R0/R1 Scope Register

| Document control | Value |
|---|---|
| Status | `PENDING_CONFIRMATION` |
| Baseline | PRD v1.0 Draft; implementation roadmap Task 1 |
| Scope authority | Business Product Owner (`PENDING_ASSIGNMENT`) |
| Required acceptance | R0 exit for the foundation baseline; R1 entry/build checkpoints for the lighthouse baseline |

## Authority statement

This register separates the approved product intent from organization-specific delivery facts. The repository does not yet identify actual entities, sources, reports, templates, users, data grains, volumes, historical periods, or release populations. Those fields are therefore `PENDING_CONFIRMATION` and **are not authoritative**. No sizing, performance, migration, coverage, or go-live claim may treat a pending row as complete.

R0 establishes controls and evidence only; it does not imply business go-live. R1 is limited to a signed representative Daily Treasury Reporting (DTR) cohort. Expansion beyond that cohort requires the applicable release gate and reconfirmed population register.

## R0 foundation baseline

| Scope category | Current authoritative value | Accountable role | Decision / deadline | Required evidence | Sign-off |
|---|---|---|---|---|---|
| Entities | `PENDING_CONFIRMATION`: legal/management entities and stable identifiers used by control fixtures and acceptance tests | Business Product Owner; Data/Source Owners | [OQ-001](open-question-register.md#oq-001) — before R0 exit | Signed entity register and synthetic-fixture mapping | `PENDING_SIGNOFF` |
| Sources | `PENDING_CONFIRMATION`: source inventory, owners, contracts, and supported access for R0 controls | Data/Source Owners | OQ-001 before R0 exit; [OQ-002](open-question-register.md#oq-002) for feed/template direction before R1 build completion | Source register, ownership evidence, approved access references | `PENDING_SIGNOFF` |
| Reports | `PENDING_CONFIRMATION`: R0 control, audit, API, security, and evidence reports; no certified business reporting population is implied | Product Owner; Finance Control Owner; Assurance Owner | OQ-001 before R0 exit | R0 report inventory and evidence-pack mapping | `PENDING_SIGNOFF` |
| Templates | `PENDING_CONFIRMATION`: synthetic templates needed to prove contracts and controls; no production template is authorized by this baseline | Data/Source Owners; Product Owner | OQ-001 before R0 exit; OQ-002 before R1 build completion | Versioned synthetic template inventory | `PENDING_SIGNOFF` |
| Users | `PENDING_CONFIRMATION`: named Product, Finance, Treasury, FP&A, Security, Operations, Assurance, engineering, and test participants | Product Owner; Security Owner | OQ-001 before R0 exit | User/role roster and access-review evidence | `PENDING_SIGNOFF` |
| Grains | `PENDING_CONFIRMATION`: entity/source/report grains and reconciliation levels for R0 examples | Data/Source Owners; Finance Control Owner | OQ-001 before R0 exit; related formula cards before certification | Grain definitions and source-contract examples | `PENDING_SIGNOFF` |
| Volumes | `PENDING_CONFIRMATION`: rows, files, payloads, revisions, cases, and audit records used by R0 tests | Business and Technical Owners | [OQ-009](open-question-register.md#oq-009) — define before performance baselining or target acceptance | Signed workload seed and fixture proportions | `PENDING_SIGNOFF` |
| Historical periods | `PENDING_CONFIRMATION`: migration-planning examples only; no historical load is authorized by R0 | Business Product Owner; Migration Owner | OQ-001 before R0 exit; [OQ-012](open-question-register.md#oq-012) before Task 15 migration/cutover design sign-off | Historical-depth decision and synthetic opening-version examples | `PENDING_SIGNOFF` |
| Release population | `PENDING_CONFIRMATION`: exact R0 environments, test cohort, controls, fixtures, and acceptance population | Business Product Owner; Acceptance Owner | OQ-001 before R0 exit | Signed R0 population and R0 evidence pack | `PENDING_SIGNOFF` |

## R1 daily treasury lighthouse baseline

| Scope category | Current authoritative value | Accountable role | Decision / deadline | Required evidence | Sign-off |
|---|---|---|---|---|---|
| Entities | `PENDING_CONFIRMATION`: representative DTR entities and restricted-scope cases | Business Product Owner; Treasury Owner | OQ-001 — confirm before R1 entry and reconfirm before each migration/wave | Signed lighthouse entity cohort | `PENDING_SIGNOFF` |
| Sources | `PENDING_CONFIRMATION`: bank, ERP, operational, market, and controlled-template sources actually used by the cohort | Data/Source Owners; Treasury Owner | OQ-002 — direct feed versus template, latency, and control totals before R1 connector/template build completion | Activated source contracts and interface decisions | `PENDING_SIGNOFF` |
| Reports | `PENDING_CONFIRMATION`: daily completion, cash/direct cash flow, facilities, intercompany, covenant, forecast, exception, and publication outputs for the cohort | Treasury Owner; Product Owner | OQ-001 before R1 entry; output-specific owners before certification | Signed output inventory and certification populations | `PENDING_SIGNOFF` |
| Templates | `PENDING_CONFIRMATION`: daily/monthly submission templates and controlled resubmission shapes | Data/Source Owners; Treasury Owner | OQ-002 before R1 build completion; OQ-009 before performance acceptance | Versioned templates, size limits, and declaration requirements | `PENDING_SIGNOFF` |
| Users | `PENDING_CONFIRMATION`: entity submitters, Treasury preparers/certifiers, reviewers, operators, and assurance participants | Treasury Owner; Security Owner; Acceptance Owner | OQ-001 before R1 entry | Role/entity roster, SoD mapping, and UAT participants | `PENDING_SIGNOFF` |
| Grains | `PENDING_CONFIRMATION`: entity, bank, account, currency, category, counterparty, facility, period, and source-record grains | Treasury Owner; Data/Source Owners | OQ-001 before R1 entry; KPI/covenant decisions before related certification | Source-contract grains and drill/reconciliation rules | `PENDING_SIGNOFF` |
| Volumes | `PENDING_CONFIRMATION`: template sizes, row counts, concurrency, network conditions, peak windows, history, and backlog | Business and Technical Owners | OQ-009 — before R1 performance baselining and target acceptance | Signed daily/monthly/peak workload definitions | `PENDING_SIGNOFF` |
| Historical periods | `PENDING_CONFIRMATION`: approved opening balances, forecast baseline, and comparison history for the lighthouse | Business Product Owner; Treasury and Migration Owners | OQ-001 before migration; OQ-012 before parallel-run and cutover planning | Historical-depth/opening-version decision and control totals | `PENDING_SIGNOFF` |
| Release population | `PENDING_CONFIRMATION`: representative cohort only; not the full R2 population | Business Product Owner; Treasury and Acceptance Owners | OQ-001 before R1 entry; OQ-012 before each cutover decision | Signed R1 cohort, parallel-run population, and exceptions | `PENDING_SIGNOFF` |

## Scope decision and change controls

1. A row becomes authoritative only when its named accountable owner records the value, evidence reference, decision date, and signature in repository-controlled evidence.
2. OQ-001 is reconfirmed for every migration and R2 wave; OQ-012 is separately decided for parallel-run duration, defect thresholds, archive/fallback, and each retirement decision.
3. Direct-feed approval does not remove template validation, immutable landing, reconciliation, or certification controls.
4. Empty, zero, unavailable, and restricted populations must remain distinguishable in the signed scope and evidence.
5. All numeric service levels and volumes remain proposed acceptance targets until measured against the signed workload and formally approved.
6. Scope additions require impact assessment covering requirements, entitlements, migration, performance, privacy, accessibility, operations, and release evidence.

## R0/R1 scope acceptance

The required Product, Finance, Treasury, FP&A, Security, Operations, and Assurance signatures are recorded in the [Control Ownership Matrix](control-ownership-matrix.md#acceptance-and-sign-off). All signatures are currently `PENDING_SIGNOFF`; this register is not yet an approved scope baseline.
