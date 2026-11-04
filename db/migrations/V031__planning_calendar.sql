-- Task 43: planning calendar, tasks, multi-level approvals, and reminders.
-- (Renumbered from the plan's V029: V025-V030 are used by Tasks 33/36/39-42.)

CREATE TABLE epm_planning_cycle (
    cycle_id            varchar(64)  PRIMARY KEY,
    plan_id             varchar(64)  NOT NULL,
    name                varchar(200) NOT NULL,
    status              varchar(16)  NOT NULL,
    business_calendar   varchar(64)  NOT NULL,
    target_business_days integer     NOT NULL DEFAULT 5,
    preparer            varchar(128) NOT NULL,
    opened_at_utc       timestamptz  NOT NULL,
    closed_at_utc       timestamptz,
    CONSTRAINT ck_cycle_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED'))
);

CREATE TABLE epm_planning_milestone (
    cycle_id         varchar(64)  NOT NULL,
    milestone_id     varchar(64)  NOT NULL,
    name             varchar(200) NOT NULL,
    due_at_utc       timestamptz  NOT NULL,
    completed_at_utc timestamptz,
    PRIMARY KEY (cycle_id, milestone_id)
);

CREATE TABLE epm_planning_task (
    task_id            varchar(64)  PRIMARY KEY,
    cycle_id           varchar(64)  NOT NULL,
    assignee           varchar(128) NOT NULL,
    delegated_from     varchar(128),
    level              integer      NOT NULL,
    title              varchar(200) NOT NULL,
    due_at_utc         timestamptz  NOT NULL,
    status             varchar(16)  NOT NULL DEFAULT 'OPEN',
    reminder_sent_utc  timestamptz,
    escalated_at_utc   timestamptz,
    completed_by       varchar(128),
    completed_at_utc   timestamptz,
    decision_comment   varchar(2000),
    CONSTRAINT ck_task_status CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED', 'ESCALATED'))
);

CREATE TABLE epm_planning_decision (
    decision_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id      varchar(64)  NOT NULL,
    actor        varchar(128) NOT NULL,
    action       varchar(16)  NOT NULL,
    comment      varchar(2000),
    acted_at_utc timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_decision_action CHECK (
        action IN ('APPROVE', 'RETURN', 'COMMENT', 'DELEGATE', 'ESCALATE', 'CANCEL',
            'REMIND', 'SUBMIT', 'LOCK'))
);
