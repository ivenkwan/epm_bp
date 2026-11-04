-- Task 41: budget/forecast plan versions, seeding, locks, and controlled reopening.
-- (Renumbered from the plan's V027: V025-V028 are used by Tasks 33/36/39/40.)

CREATE TABLE epm_plan_version (
    plan_id          varchar(64)  PRIMARY KEY,
    plan_type        varchar(16)  NOT NULL,
    name             varchar(200) NOT NULL,
    fiscal_year      integer      NOT NULL,
    status           varchar(16)  NOT NULL,
    revision         integer      NOT NULL,
    parent_plan_id   varchar(64),
    seed_source_type varchar(16),
    seed_source_ref  varchar(128),
    content_hash     varchar(128),
    owner            varchar(128) NOT NULL,
    created_at_utc   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_plan_type CHECK (plan_type IN ('BUDGET', 'FORECAST')),
    CONSTRAINT ck_plan_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'LOCKED', 'SUPERSEDED')),
    CONSTRAINT ck_seed_source CHECK (
        seed_source_type IN ('ACTUALS', 'PRIOR_PLAN', 'MANUAL', NULL))
);

CREATE TABLE epm_plan_cell (
    plan_id        varchar(64)  NOT NULL,
    dimension_key  varchar(128) NOT NULL,
    period         varchar(8)   NOT NULL,
    cell_value     numeric(24,6) NOT NULL,
    source_cell_ref varchar(128),
    PRIMARY KEY (plan_id, dimension_key, period)
);

CREATE TABLE epm_plan_assumption (
    plan_id      varchar(64)  NOT NULL,
    assumption_id varchar(64) NOT NULL,
    body         varchar(2000) NOT NULL,
    owner        varchar(128) NOT NULL,
    PRIMARY KEY (plan_id, assumption_id)
);

CREATE TABLE epm_plan_approval (
    approval_id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id       varchar(64)  NOT NULL,
    revision      integer      NOT NULL,
    approver      varchar(128) NOT NULL,
    outcome       varchar(16)  NOT NULL,
    comment       varchar(2000),
    decided_at_utc timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_plan_approval_outcome CHECK (outcome IN ('APPROVED', 'REJECTED'))
);

CREATE TABLE epm_plan_reopen_request (
    request_id    varchar(64)  PRIMARY KEY,
    plan_id       varchar(64)  NOT NULL,
    requested_by  varchar(128) NOT NULL,
    reason        varchar(2000) NOT NULL,
    scope         varchar(500) NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'REQUESTED',
    decided_by    varchar(128),
    decided_at_utc timestamptz,
    created_at_utc timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_reopen_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_reopen_reason_nonblank CHECK (length(trim(reason)) > 0),
    CONSTRAINT ck_reopen_scope_nonblank CHECK (length(trim(scope)) > 0)
);
