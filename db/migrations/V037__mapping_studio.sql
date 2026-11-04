-- Task 57: mapping studio for business-owned effective-dated mappings.
-- (Renumbered from the plan's V033: V027-V036 are used by Tasks 39-44, 48-51, 55.)

CREATE TABLE epm_mapping_table (
    table_id      varchar(64)  PRIMARY KEY,
    name          varchar(200) NOT NULL,
    table_kind    varchar(32)  NOT NULL,
    owner         varchar(128) NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_mapping_kind CHECK (
        table_kind IN ('MAPPING', 'CHART_OF_ACCOUNTS', 'ACCOUNTS', 'GROUP_MAPPING')),
    CONSTRAINT ck_mapping_status CHECK (status IN ('ACTIVE', 'RETIRED'))
);

CREATE TABLE epm_mapping_version (
    table_id       varchar(64) NOT NULL,
    version        integer     NOT NULL,
    effective_from date        NOT NULL,
    effective_to   date,
    proposed_by    varchar(128) NOT NULL,
    approved_by    varchar(128),
    activated_at_utc timestamptz,
    reason         varchar(500) NOT NULL,
    PRIMARY KEY (table_id, version),
    CONSTRAINT ck_mapping_version_approved CHECK (
        (approved_by IS NULL AND activated_at_utc IS NULL)
        OR (approved_by IS NOT NULL AND activated_at_utc IS NOT NULL))
);

CREATE TABLE epm_mapping_row (
    table_id  varchar(64)  NOT NULL,
    version   integer      NOT NULL,
    row_key   varchar(128) NOT NULL,
    maps_to   varchar(128) NOT NULL,
    group_ref varchar(128),
    PRIMARY KEY (table_id, version, row_key)
);

CREATE TABLE epm_mapping_audit (
    audit_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_id    varchar(64) NOT NULL,
    version     integer,
    actor       varchar(128) NOT NULL,
    action      varchar(16)  NOT NULL,
    old_value   varchar(2000),
    new_value   varchar(2000),
    acted_at_utc timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_mapping_action CHECK (
        action IN ('PROPOSE', 'REFUSE', 'APPROVE', 'ACTIVATE', 'ROLLBACK'))
);
