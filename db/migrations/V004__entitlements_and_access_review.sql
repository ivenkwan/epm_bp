-- Task 8: entitlements, segregation of duties, and access review.
-- (Renumbered from the plan's V003: V003 was consumed by Task 6 API safety.)

-- Authority matrix (PRD 6.8.2): role permission over (object_type, action).
-- The matrix is versioned policy: active_version selects the governing row set.
CREATE TABLE epm_policy (
    policy_id     integer PRIMARY KEY,
    version       varchar(16)  NOT NULL,
    active        boolean      NOT NULL DEFAULT false,
    description   varchar(256) NOT NULL
);

CREATE UNIQUE INDEX uq_policy_single_active ON epm_policy (active) WHERE active;

CREATE TABLE epm_permission (
    permission_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_id     integer      NOT NULL REFERENCES epm_policy (policy_id),
    role_id       varchar(64)  NOT NULL,
    object_type   varchar(64)  NOT NULL,
    action        varchar(32)  NOT NULL,
    CONSTRAINT uq_permission UNIQUE (policy_id, role_id, object_type, action),
    CONSTRAINT ck_permission_action CHECK (action IN ('READ', 'PREPARE', 'APPROVE', 'CERTIFY', 'PUBLISH', 'ADMINISTER', 'REVIEW'))
);

-- SoD constraints: a user must never hold both roles of any active pair.
CREATE TABLE epm_sod_constraint (
    sod_id   integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_id integer NOT NULL REFERENCES epm_policy (policy_id),
    role_a   varchar(64) NOT NULL,
    role_b   varchar(64) NOT NULL,
    reason   varchar(256) NOT NULL,
    CONSTRAINT uq_sod_pair UNIQUE (policy_id, role_a, role_b),
    CONSTRAINT ck_sod_distinct CHECK (role_a <> role_b)
);

-- Role grants and entity scopes with recertification deadlines. An overdue review
-- never grants access: enforcement denies and the escalation below records why.
CREATE TABLE epm_user_role (
    user_id      varchar(128),
    role_id      varchar(64)  NOT NULL,
    granted_by   varchar(128) NOT NULL,
    granted_at_utc timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE epm_entity_scope (
    user_id         varchar(128),
    entity_id       varchar(64) NOT NULL,
    granted_by      varchar(128) NOT NULL,
    granted_at_utc  timestamptz  NOT NULL DEFAULT now(),
    review_due_utc  timestamptz  NOT NULL,
    revoked_at_utc  timestamptz,
    PRIMARY KEY (user_id, entity_id)
);

-- Access recertification records: independent reviewer decisions per user and period.
CREATE TABLE epm_access_review (
    review_id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       varchar(128) NOT NULL,
    period_due    date         NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'PENDING',
    reviewer      varchar(128),
    signed_at_utc timestamptz,
    CONSTRAINT ck_review_status CHECK (status IN ('PENDING', 'SIGNED', 'OVERDUE'))
);

-- Overdue-review escalations: visible reminders to named owners; never grant access.
CREATE TABLE epm_escalation (
    escalation_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        varchar(128) NOT NULL,
    kind           varchar(32)  NOT NULL,
    detail         varchar(512) NOT NULL,
    raised_at_utc  timestamptz  NOT NULL DEFAULT now(),
    resolved_at_utc timestamptz,
    CONSTRAINT ck_escalation_kind CHECK (kind IN ('ACCESS_REVIEW_OVERDUE', 'SOD_CONFLICT', 'DENIED_ACCESS'))
);

-- Immutable decision audit for every authorization check (allow and deny).
CREATE TABLE epm_access_decision (
    decision_id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         varchar(128) NOT NULL,
    roles           varchar(512) NOT NULL,
    object_type     varchar(64)  NOT NULL,
    action          varchar(32)  NOT NULL,
    target_entity   varchar(64),
    decision        varchar(8)   NOT NULL,
    reason          varchar(256) NOT NULL,
    correlation_id  varchar(128),
    decided_at_utc  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_decision CHECK (decision IN ('ALLOW', 'DENY'))
);

CREATE INDEX ix_decision_user ON epm_access_decision (user_id, decided_at_utc);

-- Policy v1 seed: the R0 authority matrix. Actions are server-enforced regardless of
-- what the gateway or browser believe.
INSERT INTO epm_policy (policy_id, version, active, description) VALUES
    (1, 'v1', true, 'R0 authority matrix from PRD 6.8.2');

INSERT INTO epm_permission (policy_id, role_id, object_type, action) VALUES
    -- assurance readers read governed products
    (1, 'assurance-reader', 'report', 'READ'),
    (1, 'assurance-reader', 'audit', 'READ'),
    -- preparers prepare sources, submissions, adjustments; never approve
    (1, 'finance-preparer', 'source_contract', 'PREPARE'),
    (1, 'finance-preparer', 'submission', 'PREPARE'),
    (1, 'finance-preparer', 'adjustment', 'PREPARE'),
    (1, 'finance-preparer', 'submission', 'READ'),
    -- approvers approve medium-risk objects; cannot prepare them (SoD)
    (1, 'finance-approver', 'submission', 'APPROVE'),
    (1, 'finance-approver', 'adjustment', 'APPROVE'),
    (1, 'finance-approver', 'source_contract', 'APPROVE'),
    (1, 'finance-approver', 'submission', 'READ'),
    -- certifiers certify reconciliations and cash positions
    (1, 'finance-certifier', 'reconciliation', 'CERTIFY'),
    (1, 'finance-certifier', 'cash_position', 'CERTIFY'),
    (1, 'finance-certifier', 'report', 'READ'),
    -- verifiers independently verify cases and adjustments
    (1, 'finance-verifier', 'case', 'REVIEW'),
    (1, 'finance-verifier', 'adjustment', 'REVIEW'),
    -- administrators own entitlements (administered objects only)
    (1, 'finance-admin', 'entitlement', 'ADMINISTER'),
    (1, 'finance-admin', 'master_data', 'ADMINISTER');

INSERT INTO epm_sod_constraint (policy_id, role_a, role_b, reason) VALUES
    (1, 'finance-preparer', 'finance-approver', 'preparer cannot approve their own controlled work (PRD medium risk)'),
    (1, 'finance-preparer', 'finance-certifier', 'preparer cannot certify'),
    (1, 'finance-approver', 'finance-certifier', 'approver cannot certify the same controlled object');
