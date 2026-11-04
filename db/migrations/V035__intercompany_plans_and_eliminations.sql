-- Task 50: intercompany counterparty plans and management-only eliminations.
-- (Renumbered from the plan's V033: V027-V034 are used by Tasks 39-44, 48-49.)

CREATE TABLE epm_counterparty_plan (
    plan_line_id  varchar(64)  PRIMARY KEY,
    entity_id     varchar(64)  NOT NULL,
    counterparty  varchar(64)  NOT NULL,
    matching_key  varchar(128) NOT NULL,
    direction     varchar(8)   NOT NULL,
    amount        numeric(24,6) NOT NULL,
    period        varchar(8)   NOT NULL,
    plan_version  varchar(64)  NOT NULL,
    CONSTRAINT ck_cp_direction CHECK (direction IN ('PAY', 'RECEIVE'))
);

CREATE TABLE epm_elimination_run (
    run_id         varchar(64)  PRIMARY KEY,
    plan_version   varchar(64)  NOT NULL,
    label          varchar(200) NOT NULL DEFAULT 'MANAGEMENT_ONLY',
    status         varchar(16)  NOT NULL DEFAULT 'PENDING_APPROVAL',
    approved_by    varchar(128),
    executed_at_utc timestamptz,
    CONSTRAINT ck_elim_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'EXECUTED')),
    CONSTRAINT ck_elim_label CHECK (label = 'MANAGEMENT_ONLY')
);

CREATE TABLE epm_elimination_entry (
    run_id        varchar(64)   NOT NULL,
    matching_key  varchar(128)  NOT NULL,
    left_entity   varchar(64)   NOT NULL,
    right_entity  varchar(64)   NOT NULL,
    left_amount   numeric(24,6) NOT NULL,
    right_amount  numeric(24,6) NOT NULL,
    eliminated    numeric(24,6) NOT NULL,
    trace         varchar(1000) NOT NULL,
    PRIMARY KEY (run_id, matching_key)
);

CREATE TABLE epm_elimination_mismatch_case (
    case_id       varchar(64)  PRIMARY KEY,
    matching_key  varchar(128) NOT NULL,
    difference    numeric(24,6) NOT NULL,
    owner_entity  varchar(64)  NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'OPEN',
    opened_at_utc timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_mismatch_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED'))
);
