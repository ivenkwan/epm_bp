# Data Model — PostgreSQL Schema Catalog

The schema is owned by Flyway migrations `db/migrations/V001–V037` — those files are
the source of truth for columns, constraints, and CHECKs; this catalog is the map.
Every table is `epm_`-prefixed. Money is `numeric(24,6)` transported as decimal
strings; statuses and enums are CHECK-constrained; audit and ledger tables are
append-only.

## R0 — Foundation (V001–V005)

| Migration | Tables | Purpose |
|---|---|---|
| V001 core identity & revision | `epm_object`, `epm_revision` | Every governed object + gap-free append-only revisions (advisory-lock allocated) |
| V002 audit & outbox | `epm_audit_event`, `epm_audit_violation`, `epm_outbox` | Immutable old/new audit trail; transactional outbox events |
| V003 api safety | `epm_idempotency`, `epm_job` | Idempotency keys bound to (actor, operation, canonical fingerprint); durable job rows (QUEUED/RUNNING/SUCCEEDED/FAILED) |
| V004 entitlements & access review | `epm_policy`, `epm_permission`, `epm_sod_constraint`, `epm_user_role`, `epm_entity_scope`, `epm_access_review`, `epm_escalation`, `epm_access_decision` | Authorization matrix, SoD, row-level scopes, review periods |
| V005 master data | `epm_entity_version`, `epm_hierarchy_version`, `epm_hierarchy_node`, `epm_scope_activation` | Effective-dated entities and hierarchies (from-inclusive/to-exclusive, exactly-one resolution) |

## R0/R1 — Ingestion & certification (V006–V014)

| Migration | Tables | Purpose |
|---|---|---|
| V006 source contracts | `epm_source_system`, `epm_source_contract`, `epm_source_contract_transition` | Authoritative source register, contract versions with precedence |
| V007 immutable landing | `epm_landed_payload`, `epm_load_log` | Content-addressed original payloads + load evidence |
| V008 semantic catalog | `epm_kpi_card`, `epm_semantic_expression`, `epm_kpi_evaluation` | Certified KPI cards, formulas, evaluations |
| V009 cases | `epm_case`, `epm_case_transition`, `epm_case_timer`, `epm_process_correlation` | Exception cases (PRD 6.8.4 state machine), timers, process links |
| V010 ingestion runs | `epm_ingestion_schedule`, `epm_ingestion_run` | Schedules and per-run evidence (unique schedule+batch identity) |
| V011 template submissions | `epm_template_submission`, `epm_submission_row_error` | Versioned template intake, revision-per-anchor, row errors |
| V012 validation | `epm_validation_run`, `epm_quarantined_row` | Deterministic validation results and quarantined rows |
| V013 reconciliation & certification | `epm_reconciliation`, `epm_certification`, `epm_certification_rejection` | Control-total reconciliation; certification ledger (unique object+stable-id+revision — replay refuses); fail-closed rejections |
| V014 lineage | `epm_lineage_projection` | AGE-projected lineage relational shadow |

## R1 — Treasury (V015–V026)

| Migration | Tables | Purpose |
|---|---|---|
| V015 treasury calendar | `epm_treasury_calendar`, `epm_daily_submission_state`, `epm_cutoff_evaluation` | T0-inclusive cutoffs, per-day submission states |
| V016 cash position | `epm_cash_position`, `epm_cash_position_group` | Certified cash position (golden-fixture backed) |
| V021 report editions | `epm_report_edition`, `epm_publication_comment` | Editions, refresh, controlled republication, review comments |
| V022 adjustment ledger | `epm_adjustment`, `epm_adjustment_line` | Four-eyes adjustments, balanced lines, reversals |
| V023 delegation & break-glass | `epm_delegation`, `epm_break_glass_session`, `epm_break_glass_action` | Time-boxed delegation; always-denied break-glass financial authority |
| V024 exports & actions | `epm_governed_export`, `epm_distribution_log`, `epm_action_item` | Governed exports, per-recipient delivery log, tracked actions |
| V025 migration register | `epm_migration_register`, `epm_migration_batch` | Registered object classes, control totals, replay identity |
| V026 stock/unit liquidity | `epm_stock_unit_position` | Optional F-022 context (disabled default, fail-closed) |

## R3 — Reporting & planning (V027–V032)

| Migration | Tables | Purpose |
|---|---|---|
| V027 management reports | `epm_management_report`, `epm_report_section`, `epm_report_figure`, `epm_decision_pack`, `epm_pack_member`, `epm_pack_edition`, `epm_pack_approval` | Governed reports/packs; immutable editions (content hash); append-only approvals |
| V028 dimensions & FX | `epm_planning_dimension`, `epm_dimension_member`, `epm_currency_definition`, `epm_fx_version`, `epm_fx_rate` | Shared dimensions; FX versions (DRAFT/APPROVED/RETIRED) with exact-key rates |
| V029 plan versions | `epm_plan_version`, `epm_plan_cell`, `epm_plan_assumption`, `epm_plan_approval`, `epm_plan_reopen_request` | Immutable versions (seed exactly once, content hash), nonblank reopen requests |
| V030 schedules & statements | `epm_input_schedule`, `epm_schedule_line`, `epm_statement_result`, `epm_statement_line`, `epm_imbalance_case` | Non-driver schedules (NULL = missing required input), statement runs with traces, imbalance cases |
| V031 planning calendar | `epm_planning_cycle`, `epm_planning_milestone`, `epm_planning_task`, `epm_planning_decision` | Cycles, level tasks, append-only decision chronology |
| V032 KPI plans & variance | `epm_kpi_plan_value`, `epm_variance_result`, `epm_variance_commentary` | Planned KPI values with formula lineage; variance with governed percentage states; commentary actions |

## R4 — Advanced planning & governance (V033–V037)

| Migration | Tables | Purpose |
|---|---|---|
| V033 drivers & scenarios | `epm_driver`, `epm_scenario`, `epm_scenario_override`, `epm_scenario_result` | Approved drivers (ranges, versions), isolated scenario copies, traces |
| V034 allocations | `epm_allocation_model`, `epm_allocation_step`, `epm_allocation_run`, `epm_allocation_entry` | Step-down models, idempotent runs, immutable entries with traces |
| V035 eliminations | `epm_counterparty_plan`, `epm_elimination_run`, `epm_elimination_entry`, `epm_elimination_mismatch_case` | Counterparty plans; MANAGEMENT_ONLY-labeled runs (schema CHECK); mismatch cases |
| V036 AI provenance | `epm_certified_snapshot`, `epm_ai_provenance`, `epm_ai_route_state` | Entitlement-scoped snapshots; route/model/prompt/output provenance (incl. REFUSED); per-route kill switch |
| V037 mapping studio | `epm_mapping_table`, `epm_mapping_version`, `epm_mapping_row`, `epm_mapping_audit` | Effective-dated mappings (charts/accounts/groups); versions + append-only audit — no history loss |

## Invariants across the schema

1. **Append-only ledgers** (`epm_revision`, `epm_audit_event`, `epm_certification`,
   `epm_allocation_entry`, `epm_elimination_entry`, `epm_mapping_audit`, …):
   corrections are new rows/reversals — nothing is updated or deleted.
2. **Unique business effects**: certification and elimination runs carry uniqueness
   that turns replays into governed refusals.
3. **Labels enforce boundaries**: `epm_elimination_run.label` is CHECK-locked to
   `MANAGEMENT_ONLY` (F-043).
4. **Nonblank governance**: reopen reasons/scopes and mapping reasons are
   CHECK-enforced nonblank.
