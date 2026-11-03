-- Task 19: deterministic validation results and quarantine.
-- (Renumbered from the plan's V011: V003-V011 were consumed by Tasks 6-18.)

CREATE TABLE epm_validation_run (
    validation_id   uuid PRIMARY KEY,
    load_id         uuid         NOT NULL,
    rule_version    varchar(32)  NOT NULL,
    passed          boolean      NOT NULL,
    quarantined_rows integer    NOT NULL DEFAULT 0,
    passed_rows     integer      NOT NULL DEFAULT 0,
    evaluated_at_utc timestamptz NOT NULL DEFAULT now(),
    -- Reproducibility: the same landed input and rule version yield the same outcome;
    -- one validation per (load, rule version) makes replay return the original result.
    CONSTRAINT uq_validation_per_load UNIQUE (load_id, rule_version)
);

CREATE TABLE epm_quarantined_row (
    quarantine_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    validation_id  uuid        NOT NULL,
    row_number     integer     NOT NULL,
    rule_name      varchar(64) NOT NULL,
    expected       varchar(128),
    actual         varchar(128),
    explanation    varchar(256) NOT NULL,
    case_id        varchar(64),
    source_revision varchar(64) NOT NULL,
    quarantined_at_utc timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_quarantine_validation ON epm_quarantined_row (validation_id);
