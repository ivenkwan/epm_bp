-- Task 18: versioned template submissions with controlled resubmission.
-- (Renumbered from the plan's V010: V003-V010 were consumed by Tasks 6-17.)

CREATE TABLE epm_template_submission (
    submission_id    varchar(64) PRIMARY KEY,
    entity_id        varchar(64)  NOT NULL,
    contract_id      varchar(64)  NOT NULL,
    source_period    varchar(16)  NOT NULL,
    template_version varchar(32)  NOT NULL,
    revision         integer      NOT NULL,
    status           varchar(16)  NOT NULL,
    submitted_by     varchar(128) NOT NULL,
    submitted_at_utc timestamptz  NOT NULL DEFAULT now(),
    load_id          uuid,
    -- Controlled resubmission: a corrected submission is a NEW revision of the same
    -- (entity, contract, period) anchor; the original is never overwritten.
    CONSTRAINT uq_submission_revision UNIQUE (entity_id, contract_id, source_period, revision),
    CONSTRAINT ck_submission_status CHECK (status IN ('VALIDATING', 'ACCEPTED', 'REJECTED'))
);

CREATE INDEX ix_submission_anchor ON epm_template_submission (entity_id, contract_id, source_period);

CREATE TABLE epm_submission_row_error (
    error_id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_id varchar(64) NOT NULL,
    row_number   integer     NOT NULL,
    column_name  varchar(64),
    code         varchar(32) NOT NULL,
    message      varchar(256) NOT NULL
);

CREATE INDEX ix_row_error_submission ON epm_submission_row_error (submission_id);
