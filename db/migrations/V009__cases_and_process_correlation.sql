-- Task 14: durable exception cases and process correlation.
-- (Renumbered from the plan's V008: V003-V008 were consumed by Tasks 6-13.)

CREATE TYPE epm_case_state AS ENUM (
    'OPEN', 'TRIAGED', 'ASSIGNED', 'IN_PROGRESS',
    'WAITING_SOURCE', 'WAITING_EVIDENCE', 'WAITING_APPROVAL',
    'RESOLUTION_PROPOSED', 'RESOLUTION_APPROVED', 'REPROCESSING',
    'VERIFIED', 'CLOSED', 'CANCELLED'
);

CREATE TABLE epm_case (
    case_id           varchar(64) PRIMARY KEY,
    case_type         varchar(64)  NOT NULL,
    severity          varchar(16)  NOT NULL,
    status            epm_case_state NOT NULL DEFAULT 'OPEN',
    business_object_type varchar(64),
    business_object_id   varchar(128),
    reporter          varchar(128) NOT NULL,
    accountable_owner varchar(128),
    assigned_resolver varchar(128),
    required_approver varchar(128),
    independent_verifier varchar(128),
    trigger           jsonb        NOT NULL,
    evidence          jsonb        NOT NULL,
    decision          jsonb,
    outcome           jsonb,
    process_instance_id varchar(128),
    correlation_id    varchar(128),
    created_at_utc    timestamptz  NOT NULL DEFAULT now(),
    updated_at_utc    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_case_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX ix_case_status ON epm_case (status, updated_at_utc);
CREATE INDEX ix_case_correlation ON epm_case (correlation_id);

-- Legal state-machine transitions (PRD 6.8.4); timers are advisory only.
CREATE TABLE epm_case_transition (
    transition_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id        varchar(64)    NOT NULL,
    from_state     varchar(24)    NOT NULL,
    to_state       varchar(24)    NOT NULL,
    actor          varchar(128)   NOT NULL,
    note           varchar(512),
    at_utc         timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT ck_case_transition CHECK (from_state <> to_state)
);

-- Timer register: reminders, reassignments, escalations, and visibility changes.
-- A timer can NEVER approve, close, certify, publish, post, pay, or adjust.
CREATE TABLE epm_case_timer (
    timer_id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id      varchar(64)  NOT NULL,
    milestone    varchar(24)  NOT NULL,
    due_at_utc   timestamptz  NOT NULL,
    fired_at_utc timestamptz,
    action       varchar(24)  NOT NULL,
    CONSTRAINT ck_timer_action CHECK (action IN ('REMIND', 'REASSIGN', 'ESCALATE', 'VISIBILITY')),
    CONSTRAINT ck_timer_milestone CHECK (milestone IN ('HALF', 'THREE_QUARTER', 'BREACH', 'CUSTOM'))
);

-- Correlation between Kogito processes and the PostgreSQL-owned case state.
CREATE TABLE epm_process_correlation (
    correlation_key     varchar(128) PRIMARY KEY,
    process_instance_id varchar(128) NOT NULL,
    case_id             varchar(64)  NOT NULL,
    process_version     varchar(32)  NOT NULL,
    started_at_utc      timestamptz  NOT NULL DEFAULT now()
);
