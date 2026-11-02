-- Task 12: certified KPI and semantic catalog.
-- (Renumbered from the plan's V007: V003-V007 were consumed by Tasks 6-11.)

CREATE TABLE epm_kpi_card (
    kpi_id           varchar(64) PRIMARY KEY,
    name             varchar(128) NOT NULL,
    formula          varchar(512) NOT NULL,
    unit             varchar(32)  NOT NULL,
    grain            varchar(64)  NOT NULL,
    dimensions       jsonb        NOT NULL,
    owner            varchar(128) NOT NULL,
    cadence          varchar(32)  NOT NULL,
    sensitivity      varchar(16)  NOT NULL,
    source_mappings  jsonb        NOT NULL,
    thresholds       jsonb        NOT NULL,
    worked_example   jsonb        NOT NULL,
    drill_policy     varchar(32)  NOT NULL,
    version          integer      NOT NULL DEFAULT 1,
    status           varchar(16)  NOT NULL DEFAULT 'DRAFT',
    certified_by     varchar(128),
    certified_at_utc timestamptz,
    deprecated_at_utc timestamptz,
    CONSTRAINT ck_kpi_status CHECK (status IN ('DRAFT', 'TESTED', 'CERTIFIED', 'DEPRECATED')),
    CONSTRAINT ck_kpi_sensitivity CHECK (sensitivity IN ('PUBLIC', 'INTERNAL', 'RESTRICTED')),
    CONSTRAINT ck_kpi_certified CHECK (status <> 'CERTIFIED' OR certified_by IS NOT NULL)
);

-- Allowlisted expression registry: a KPI formula may only reference registered
-- expressions; arbitrary user code is structurally impossible.
CREATE TABLE epm_semantic_expression (
    expression_id    varchar(64) PRIMARY KEY,
    kind             varchar(16)  NOT NULL,
    definition       varchar(512) NOT NULL,
    description      varchar(256) NOT NULL,
    CONSTRAINT ck_expression_kind CHECK (kind IN ('AGGREGATE', 'RATIO', 'DELTA', 'CONSTANT'))
);

INSERT INTO epm_semantic_expression (expression_id, kind, definition, description) VALUES
    ('sum', 'AGGREGATE', 'SUM(measure)', 'Sum of a mapped measure'),
    ('gearing', 'RATIO', 'numerator / denominator', 'Debt-to-equity style ratio'),
    ('headroom', 'DELTA', 'limit - utilization', 'Limit minus utilization'),
    ('one', 'CONSTANT', '1', 'Unity constant');

-- Certification preconditions are durable evidence, not transient checks.
CREATE TABLE epm_kpi_evaluation (
    evaluation_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kpi_id           varchar(64) NOT NULL,
    kpi_version      integer     NOT NULL,
    input_snapshot   jsonb       NOT NULL,
    result_value     varchar(64),
    result_status    varchar(40) NOT NULL,
    evaluated_at_utc timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_kpi_eval_status CHECK (
        result_status IN ('OK', 'UNAVAILABLE_NULL_NUMERATOR', 'UNAVAILABLE_ZERO_DENOMINATOR',
                          'UNDEFINED_MEASURE', 'QUALITY_FLAGGED')
    ) -- length widened to 40: UNAVAILABLE_ZERO_DENOMINATOR is 30 characters
);
