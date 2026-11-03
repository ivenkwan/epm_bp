-- Task 22: daily treasury calendar, entity cutoffs, and submission lateness.
-- (Renumbered from the plan's V013: V003-V014 were consumed by Tasks 6-21.)

CREATE TABLE epm_treasury_calendar (
    entity_id     varchar(64) PRIMARY KEY,
    timezone      varchar(64)  NOT NULL,
    cutoff_utc    time         NOT NULL,
    owner         varchar(128) NOT NULL
);

CREATE TABLE epm_daily_submission_state (
    entity_id      varchar(64),
    business_date  date,
    status         varchar(16) NOT NULL,
    last_revision  varchar(64),
    received_at_utc timestamptz,
    freshness_as_of timestamptz,
    updated_at_utc timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (entity_id, business_date),
    CONSTRAINT ck_daily_status CHECK (status IN ('PENDING', 'ON_TIME', 'LATE', 'MISSING'))
);

-- One row per evaluated cutoff: the daily completion matrix source.
CREATE TABLE epm_cutoff_evaluation (
    evaluation_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_date  date         NOT NULL,
    entity_id      varchar(64)  NOT NULL,
    outcome        varchar(16)  NOT NULL,
    received_at_utc timestamptz,
    cutoff_utc     timestamptz NOT NULL,
    evaluated_at_utc timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_cutoff_outcome CHECK (outcome IN ('ON_TIME', 'LATE_AT_T0_PLUS', 'MISSING'))
);
