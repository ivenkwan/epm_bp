-- Task 44: actuals variance and shared KPI planning.
-- (Renumbered from the plan's V030: V025-V031 are used by Tasks 33/36/39-43.)

CREATE TABLE epm_kpi_plan_value (
    plan_id         varchar(64)  NOT NULL,
    kpi_id          varchar(64)  NOT NULL,
    dimension_key   varchar(128) NOT NULL,
    period          varchar(8)   NOT NULL,
    planned_value   numeric(24,6) NOT NULL,
    formula_version varchar(32)  NOT NULL,
    source_ref      varchar(256) NOT NULL,
    PRIMARY KEY (plan_id, kpi_id, dimension_key, period)
);

CREATE TABLE epm_variance_result (
    variance_id        varchar(64)  PRIMARY KEY,
    plan_id            varchar(64)  NOT NULL,
    kpi_id             varchar(64)  NOT NULL,
    dimension_key      varchar(128) NOT NULL,
    period             varchar(8)   NOT NULL,
    comparison_basis   varchar(16)  NOT NULL,
    actual_value       numeric(24,6),
    plan_value         numeric(24,6),
    amount_variance    numeric(24,6),
    percentage_variance numeric(10,6),
    percentage_state   varchar(24)  NOT NULL,
    actual_certified   boolean      NOT NULL,
    formula_version    varchar(32)  NOT NULL,
    created_at_utc     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_variance_basis CHECK (
        comparison_basis IN ('BUDGET', 'FORECAST', 'PRIOR_PERIOD')),
    CONSTRAINT ck_percentage_state CHECK (
        percentage_state IN ('COMPUTED', 'UNDEFINED_ZERO_BASELINE', 'UNAVAILABLE'))
);

CREATE TABLE epm_variance_commentary (
    commentary_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    variance_id    varchar(64)  NOT NULL,
    author         varchar(128) NOT NULL,
    body           varchar(2000) NOT NULL,
    action         varchar(16),
    action_owner   varchar(128),
    created_at_utc timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_commentary_action CHECK (
        action IN ('EXPLAIN', 'MONITOR', 'FOLLOW_UP', NULL))
);
