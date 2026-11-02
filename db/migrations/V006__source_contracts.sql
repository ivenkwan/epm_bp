-- Task 10: authoritative source register and data contracts.
-- (Renumbered from the plan's V005: V003-V005 were consumed by Tasks 6-9.)

CREATE TABLE epm_source_system (
    system_id    varchar(64) PRIMARY KEY,
    system_name  varchar(128) NOT NULL,
    owner        varchar(128) NOT NULL,
    kind         varchar(32)  NOT NULL,
    CONSTRAINT ck_source_kind CHECK (kind IN ('ERP', 'BANK', 'SPREADSHEET', 'API', 'MARKET_DATA'))
);

-- Versioned data contracts. Exactly one contract may be authoritative for a declared
-- (scope, period); ambiguous claims are rejected by the unique index on active rows.
CREATE TABLE epm_source_contract (
    contract_id    varchar(64),
    version        integer      NOT NULL,
    system_id      varchar(64)  NOT NULL REFERENCES epm_source_system (system_id),
    owner          varchar(128) NOT NULL,
    scope          varchar(64)  NOT NULL,
    grain          varchar(64)  NOT NULL,
    cadence        varchar(32)  NOT NULL,
    cutoff_utc     time         NOT NULL,
    field_definitions jsonb     NOT NULL,
    quality_rules  jsonb        NOT NULL,
    reconciliation_basis jsonb  NOT NULL,
    retention_months integer   NOT NULL,
    credentials_ref varchar(256),
    precedence     integer      NOT NULL DEFAULT 100,
    status         varchar(16)  NOT NULL DEFAULT 'PROPOSED',
    deprecated_at_utc timestamptz,
    proposed_by    varchar(128) NOT NULL,
    approved_by    varchar(128),
    created_at_utc timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (contract_id, version),
    CONSTRAINT ck_contract_status CHECK (status IN ('PROPOSED', 'APPROVED', 'ACTIVE', 'DEPRECATED', 'REJECTED')),
    CONSTRAINT ck_contract_transition CHECK (
        status <> 'ACTIVE' OR approved_by IS NOT NULL
    )
);

-- Single-authority lookup index. The exactly-one-ACTIVE invariant is enforced in the
-- service layer (with approved precedence resolving claims): a hard partial unique
-- index cannot express the atomic ACTIVE→DEPRECATED swap a precedence supersedes.
CREATE INDEX ix_active_contract_per_scope
    ON epm_source_contract (scope) WHERE status = 'ACTIVE';

-- Synthetic source systems referenced by the contract suite.
INSERT INTO epm_source_system (system_id, system_name, owner, kind) VALUES
    ('SYS-ERP-1', 'Synthetic ERP One', 'source-owner@example.invalid', 'ERP'),
    ('SYS-BANK-1', 'Synthetic Bank Feed', 'treasury@example.invalid', 'BANK'),
    ('SYS-TMPL-1', 'Synthetic Template Uploads', 'fpna@example.invalid', 'SPREADSHEET');

CREATE TABLE epm_source_contract_transition (
    transition_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contract_id   varchar(64)  NOT NULL,
    version       integer      NOT NULL,
    from_status   varchar(16)  NOT NULL DEFAULT 'NONE',
    to_status     varchar(16)  NOT NULL,
    actor         varchar(128) NOT NULL,
    reason        varchar(512),
    at_utc        timestamptz  NOT NULL DEFAULT now()
);
