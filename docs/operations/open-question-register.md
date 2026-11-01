# Open Question Register

| Document control | Value |
|---|---|
| Status | Active decision baseline |
| Source | PRD §12.4 and implementation roadmap coverage matrix |
| Decision convention | `OPEN` until a named accountable human records a decision and evidence |

An owner role identifies accountability, not acceptance. `PENDING_ASSIGNMENT` means no personal name is available in the repository; `PENDING_DECISION` means no decision is approved. Deadlines are release-relative because no calendar dates were supplied.

Escalation follows the [Control Ownership Matrix](control-ownership-matrix.md#ownership-assignments): owner → domain/Product owner → control/Security owner where applicable → independent Assurance or authorized incident command for consequential delays. Missing a deadline blocks the dependent build, certification, UAT, pilot, cutover, or release claim; it does not trigger an automatic approval or default decision.

| ID | Decision required | Accountable role / named person | Owning tasks | Required decision point and reconfirmation | Status / decision evidence |
|---|---|---|---|---|---|
| <a id="oq-001"></a>OQ-001 | Which entities, sources, reports, templates, users, and historical periods are authoritative for each release? | Business Product Owner / `PENDING_ASSIGNMENT` | 1;33;35 | Before R0 exit; reconfirm every migration and R2 wave | OPEN / `PENDING_DECISION` |
| <a id="oq-002"></a>OQ-002 | Which sources use direct integration versus validated templates, and what latency and control totals apply? | Data/Source Owners / `PENDING_ASSIGNMENT` | 10;17;18 | Before R1 connector/template build completion; confirm for each activated source contract | OPEN / `PENDING_DECISION` |
| <a id="oq-003"></a>OQ-003 | Which KPI and covenant formula cards, grains, thresholds, owners, and examples are approved? | Finance and Treasury Semantic Owners / `PENDING_ASSIGNMENT` | 12;26 | Before related semantic or covenant certification and every material formula change | OPEN / `PENDING_DECISION` |
| <a id="oq-004"></a>OQ-004 | What daily/weekly/monthly cutoffs, business calendars, partial-publication rules, and severity targets apply? | Process Owners / `PENDING_ASSIGNMENT` | 22;28;34 | Before R1 UAT and parallel runs; reconfirm when calendars or coverage change | OPEN / `PENDING_DECISION` |
| <a id="oq-005"></a>OQ-005 | What forecast horizon, update cadence, period count, and actual-comparison basis are required? | Treasury Owner / `PENDING_ASSIGNMENT` | 27 | Before F-021 model acceptance | OPEN / `PENDING_DECISION` |
| <a id="oq-006"></a>OQ-006 | What planning approval levels, integrated-statement rules, lock/reopen policy, and retention apply? | FP&A Owner / `PENDING_ASSIGNMENT` | 41;42;43;46 | Before R3 UAT; reconfirm for material planning-policy changes | OPEN / `PENDING_DECISION` |
| <a id="oq-007"></a>OQ-007 | Which driver, scenario, allocation, intercompany, and management-elimination rules are material enough for R4? | FP&A/Model Owners / `PENDING_ASSIGNMENT` | 46;48;49;50 | At R3 exit, before R4 model build | OPEN / `PENDING_DECISION` |
| <a id="oq-008"></a>OQ-008 | What hosting regions, retention, privacy, support-access, RPO/RTO, and no-training policies are approved? | Security/Privacy/Operations Owners / `PENDING_ASSIGNMENT` | 1;4;37;51 | Before production and before any AI route; reconfirm on provider, region, route, or policy change | OPEN / `PENDING_DECISION` |
| <a id="oq-009"></a>OQ-009 | What template sizes, row counts, concurrency, networks, and peak windows define performance acceptance? | Business and Technical Owners / `PENDING_ASSIGNMENT` | 1;3;34 | Before performance baselining and target acceptance; reconfirm for each signed workload or population change | OPEN / `PENDING_DECISION` |
| <a id="oq-010"></a>OQ-010 | Which languages, browsers, assistive technologies, export formats, and mobile-browser journeys are mandatory? | Product Owner / `PENDING_ASSIGNMENT` | 32;45;47 | Before UX acceptance; reconfirm before adding or dropping supported journeys | OPEN / `PENDING_DECISION` |
| <a id="oq-011"></a>OQ-011 | Which AI routes and use cases are permitted, and who approves model, prompt, and publication changes? | AI, Security, and Domain Owners / `PENDING_ASSIGNMENT` | 51;52;53;54 | Before every AI pilot and every model or prompt change | OPEN / `PENDING_DECISION` |
| <a id="oq-012"></a>OQ-012 | What parallel-run duration, defect thresholds, archive/fallback arrangements, and retirement decisions apply? | Business/Control Owners / `PENDING_ASSIGNMENT` | 15;33;37;46 | Before each cutover; legacy retirement always requires a separate human decision | OPEN / `PENDING_DECISION` |

## Decision record requirements

A question may move from `OPEN` to `DECIDED` only when its row or linked decision record contains:

1. The selected decision, affected scope, and effective/release date.
2. The named accountable decision-maker and required independent approvers.
3. Source evidence, assumptions, rejected alternatives, and downstream impacts.
4. Reconfirmation triggers, owning tasks, and updates to the scope register, traceability register, contracts, tests, runbooks, and user/admin documentation as applicable.

An open question must never be closed merely because an implementation exists. Numeric targets, recovery objectives, response thresholds, and adoption goals remain proposed until measured and formally approved.
