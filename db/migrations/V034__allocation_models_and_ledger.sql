-- Task 49: multi-step and step-down allocation models and immutable ledger.
-- (Renumbered from the plan's V032: V027-V033 are used by Tasks 39-44, 48.)

CREATE TABLE epm_allocation_model (
    model_id       varchar(64)  PRIMARY KEY,
    name           varchar(200) NOT NULL,
    rule_version   varchar(32)  NOT NULL,
    residual_rule  varchar(32)  NOT NULL DEFAULT 'LARGEST_REMAINDER',
    status         varchar(16)  NOT NULL DEFAULT 'DRAFT',
    approved_by    varchar(128),
    created_at_utc timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_alloc_status CHECK (status IN ('DRAFT', 'SIMULATED', 'APPROVED', 'EXECUTED')),
    CONSTRAINT ck_residual_rule CHECK (residual_rule IN ('LARGEST_REMAINDER'))
);

CREATE TABLE epm_allocation_step (
    model_id    varchar(64) NOT NULL,
    step_number integer     NOT NULL,
    sender      varchar(64) NOT NULL,
    receivers   varchar(500) NOT NULL,
    drivers     varchar(500) NOT NULL,
    PRIMARY KEY (model_id, step_number)
);

CREATE TABLE epm_allocation_run (
    run_id         varchar(64)  PRIMARY KEY,
    model_id       varchar(64)  NOT NULL,
    reversal_of    varchar(64),
    status         varchar(16)  NOT NULL DEFAULT 'EXECUTED',
    executed_by    varchar(128) NOT NULL,
    executed_at_utc timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_run_status CHECK (status IN ('EXECUTED', 'REVERSED'))
);

CREATE TABLE epm_allocation_entry (
    run_id     varchar(64)   NOT NULL,
    step_number integer      NOT NULL,
    source_pool varchar(64)  NOT NULL,
    receiver   varchar(64)   NOT NULL,
    amount     numeric(24,6) NOT NULL,
    trace      varchar(1000) NOT NULL,
    PRIMARY KEY (run_id, step_number, source_pool, receiver)
);
