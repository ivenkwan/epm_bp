-- Task 20: reconciliation statements and independent certification.
-- (Renumbered from the plan's V012: V003-V012 were consumed by Tasks 6-19.)

CREATE TABLE epm_reconciliation (
    reconciliation_id varchar(64) PRIMARY KEY,
    load_id           uuid        NOT NULL,
    rule_version      varchar(32) NOT NULL,
    source_total      numeric(24,6) NOT NULL,
    loaded_total      numeric(24,6) NOT NULL,
    aggregate_total   numeric(24,6) NOT NULL,
    difference        numeric(24,6) NOT NULL,
    tolerance         numeric(24,6) NOT NULL,
    status            varchar(32)  NOT NULL,
    explanation       varchar(512),
    reconciled_at_utc timestamptz  NOT NULL DEFAULT now(),
    reconciled_by     varchar(128) NOT NULL,
    CONSTRAINT ck_reconciliation_status CHECK (
        status IN ('MATCHED_EXACT', 'MATCHED_AT_EXACT_TOLERANCE', 'BLOCKED_OVER_TOLERANCE')
    )
);

-- One certification per controlled (object_type, stable_id, revision): the human trust
-- gate. Preconditions are enforced in the service and each row carries the full
-- evidence bundle reference.
CREATE TABLE epm_certification (
    certification_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_type       varchar(64)  NOT NULL,
    stable_id         varchar(128) NOT NULL,
    revision          integer      NOT NULL,
    validation_run_id uuid,
    reconciliation_id varchar(64),
    evidence_bundle   varchar(128) NOT NULL,
    certifier         varchar(128) NOT NULL,
    certified_at_utc  timestamptz  NOT NULL DEFAULT now(),
    correlation_id    varchar(128),
    CONSTRAINT uq_certified_revision UNIQUE (object_type, stable_id, revision)
);

-- Rejection history: a refused certification never disappears.
CREATE TABLE epm_certification_rejection (
    rejection_id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_type    varchar(64)  NOT NULL,
    stable_id      varchar(128) NOT NULL,
    revision       integer      NOT NULL,
    certifier      varchar(128) NOT NULL,
    reason         varchar(512) NOT NULL,
    rejected_at_utc timestamptz NOT NULL DEFAULT now()
);
