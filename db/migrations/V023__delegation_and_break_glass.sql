-- Task 30: delegation, escalation, and break-glass control.
-- (Renumbered from the plan's V021: V021 is used by Task 28; this lands as V023.)

CREATE TABLE epm_delegation (
    delegation_id    varchar(64) PRIMARY KEY,
    delegator        varchar(128) NOT NULL,
    delegate         varchar(128) NOT NULL,
    entity_scope     varchar(64)  NOT NULL,
    permitted_action varchar(32)  NOT NULL,
    valid_from_utc   timestamptz  NOT NULL,
    valid_to_utc     timestamptz  NOT NULL,
    revoked_at_utc   timestamptz,
    created_at_utc   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_delegation_window CHECK (valid_to_utc > valid_from_utc),
    CONSTRAINT ck_delegation_distinct CHECK (delegator <> delegate)
);

CREATE TABLE epm_break_glass_session (
    session_id       varchar(64) PRIMARY KEY,
    requester        varchar(128) NOT NULL,
    approver         varchar(128) NOT NULL,
    scope            varchar(128) NOT NULL,
    justification    varchar(512) NOT NULL,
    started_at_utc   timestamptz  NOT NULL DEFAULT now(),
    expires_at_utc   timestamptz  NOT NULL,
    revoked_at_utc   timestamptz,
    reviewed_by      varchar(128),
    reviewed_at_utc  timestamptz,
    CONSTRAINT ck_breakglass_distinct CHECK (requester <> approver)
);

CREATE TABLE epm_break_glass_action (
    action_id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id  varchar(64)  NOT NULL,
    actor       varchar(128) NOT NULL,
    action      varchar(128) NOT NULL,
    allowed     boolean      NOT NULL,
    reason      varchar(512),
    at_utc      timestamptz  NOT NULL DEFAULT now()
);
