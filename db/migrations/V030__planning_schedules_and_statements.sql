-- Task 42: non-driver input schedules and integrated P&L/BS/CF statements.
-- (Renumbered from the plan's V028: V025-V029 are used by Tasks 33/36/39-41.)

CREATE TABLE epm_input_schedule (
    schedule_id    varchar(64)  PRIMARY KEY,
    plan_id        varchar(64)  NOT NULL,
    schedule_type  varchar(32)  NOT NULL,
    version        integer      NOT NULL,
    status         varchar(16)  NOT NULL,
    created_at_utc timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_schedule_type CHECK (
        schedule_type IN ('REVENUE', 'OPERATING_COST', 'CAPITAL', 'WORKFORCE',
            'OPENING_BALANCE')),
    CONSTRAINT ck_schedule_status CHECK (status IN ('DRAFT', 'VALID', 'INVALID'))
);

CREATE TABLE epm_schedule_line (
    schedule_id varchar(64)   NOT NULL,
    entity_id   varchar(64)   NOT NULL,
    period      varchar(8)    NOT NULL,
    line_key    varchar(128)  NOT NULL,
    cell_value  numeric(24,6),
    PRIMARY KEY (schedule_id, entity_id, period, line_key)
);

CREATE TABLE epm_statement_result (
    result_id       varchar(64)  PRIMARY KEY,
    plan_id         varchar(64)  NOT NULL,
    entity_id       varchar(64)  NOT NULL,
    period          varchar(8)   NOT NULL,
    statement_type  varchar(8)   NOT NULL,
    model_version   varchar(32)  NOT NULL,
    rule_version    varchar(32)  NOT NULL,
    calc_trace      varchar(2000) NOT NULL,
    balanced        boolean      NOT NULL,
    computed_at_utc timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_statement_type CHECK (statement_type IN ('PL', 'BS', 'CF'))
);

CREATE TABLE epm_statement_line (
    result_id         varchar(64)  NOT NULL,
    line_key          varchar(128) NOT NULL,
    amount            numeric(24,6) NOT NULL,
    schedule_line_ref varchar(256),
    PRIMARY KEY (result_id, line_key)
);

CREATE TABLE epm_imbalance_case (
    case_id       varchar(64)  PRIMARY KEY,
    plan_id       varchar(64)  NOT NULL,
    entity_id     varchar(64)  NOT NULL,
    period        varchar(8)   NOT NULL,
    statement_type varchar(8)  NOT NULL,
    schedule_id   varchar(64),
    difference    numeric(24,6) NOT NULL,
    model_version varchar(32)  NOT NULL,
    rule_version  varchar(32)  NOT NULL,
    calc_trace    varchar(2000) NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'OPEN',
    created_at_utc timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_imbalance_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED'))
);
