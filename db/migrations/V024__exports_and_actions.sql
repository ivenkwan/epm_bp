-- Task 31: governed exports, distribution, and action tracking base.
-- (Renumbered from the plan's V022: V022 is used by Task 29; this lands as V024.)

CREATE TABLE epm_governed_export (
    export_id      varchar(64) PRIMARY KEY,
    edition_id     varchar(64)  NOT NULL,
    format         varchar(8)   NOT NULL,
    recipient      varchar(128) NOT NULL,
    row_count      integer      NOT NULL,
    row_limit      integer      NOT NULL,
    content        text         NOT NULL,
    correlation_id varchar(128),
    created_at_utc timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_export_format CHECK (format IN ('CSV', 'PDF')),
    CONSTRAINT ck_export_rows CHECK (row_count >= 0)
);

CREATE TABLE epm_distribution_log (
    delivery_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    export_id      varchar(64)  NOT NULL,
    recipient      varchar(128) NOT NULL,
    delivered_at_utc timestamptz NOT NULL DEFAULT now(),
    retention_class varchar(16) NOT NULL,
    CONSTRAINT ck_retention_class CHECK (retention_class IN ('INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'))
);

CREATE TABLE epm_action_item (
    action_id     varchar(64) PRIMARY KEY,
    description   varchar(512) NOT NULL,
    owner         varchar(128) NOT NULL,
    due_at_utc    timestamptz  NOT NULL,
    decision      varchar(64),
    decided_by    varchar(128),
    decided_at_utc timestamptz,
    completion_evidence varchar(256),
    verified_by   varchar(128),
    created_at_utc timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_action_verification CHECK (decided_by IS NULL OR verified_by IS NULL
        OR decided_by <> verified_by)
);
