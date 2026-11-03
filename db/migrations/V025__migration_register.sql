-- Task 33: migration register (initial historical migration control).
-- (Renumbered from the plan's V023: V023 is used by Task 30; this lands as V025.)

CREATE TABLE epm_migration_register (
    object_class    varchar(64) PRIMARY KEY,
    legacy_system   varchar(64)  NOT NULL,
    depth_months    integer      NOT NULL,
    opening_version varchar(32)  NOT NULL,
    mapping_set     varchar(64)  NOT NULL,
    control_total_rows  integer  NOT NULL,
    control_total_amount numeric(24,6) NOT NULL,
    cutoff_inclusive boolean     NOT NULL,
    replay_identity varchar(64)  NOT NULL,
    archive_ref     varchar(256),
    status          varchar(16)  NOT NULL,
    decided_by      varchar(128),
    decided_at_utc  timestamptz,
    CONSTRAINT ck_migration_status CHECK (
        status IN ('REGISTERED', 'LOADED', 'RECONCILED', 'GO', 'NO_GO', 'FALLBACK'))
);

CREATE TABLE epm_migration_batch (
    batch_id     varchar(64) PRIMARY KEY,
    object_class varchar(64)  NOT NULL,
    run_number   integer      NOT NULL,
    row_count    integer      NOT NULL,
    total_amount numeric(24,6) NOT NULL,
    outcome      varchar(16)  NOT NULL,
    loaded_at_utc timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_batch_outcome CHECK (outcome IN ('LOADED', 'DUPLICATE_REPLAY', 'BLOCKED_TOLERANCE'))
);
