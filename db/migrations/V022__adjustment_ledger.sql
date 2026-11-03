-- Task 29: manual adjustment ledger with four-eyes execution.
-- (Renumbered from the plan's V020: V003-V016 consumed by Tasks 6-23; V017-V021
-- by Tasks 24-28 as planned.)

CREATE TABLE epm_adjustment (
    adjustment_id   varchar(64) PRIMARY KEY,
    entity_id       varchar(64)  NOT NULL,
    currency        varchar(3)   NOT NULL,
    reason          varchar(512) NOT NULL,
    evidence_ref    varchar(128) NOT NULL,
    materiality     numeric(24,6) NOT NULL,
    approval_level  varchar(32)  NOT NULL,
    status          varchar(16)  NOT NULL,
    proposed_by     varchar(128) NOT NULL,
    approved_by     varchar(128),
    applied_by      varchar(128),
    reversed_ref    varchar(64),
    created_at_utc  timestamptz  NOT NULL DEFAULT now(),
    applied_at_utc  timestamptz,
    CONSTRAINT ck_adjustment_status CHECK (
        status IN ('PROPOSED', 'REJECTED', 'APPROVED', 'APPLIED', 'REVERSED')),
    CONSTRAINT ck_adjustment_level CHECK (
        approval_level IN ('OWNER_REVIEW', 'INDEPENDENT_APPROVER',
                           'DOMAIN_APPROVER_PLUS_CERTIFIER'))
);

CREATE TABLE epm_adjustment_line (
    adjustment_id varchar(64)  NOT NULL,
    line_number   integer      NOT NULL,
    account_id    varchar(64)  NOT NULL,
    side          varchar(1)   NOT NULL,
    amount        numeric(24,6) NOT NULL,
    PRIMARY KEY (adjustment_id, line_number),
    CONSTRAINT ck_adjustment_side CHECK (side IN ('D', 'C'))
);
