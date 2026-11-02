-- Task 5: core identity and revision model.
-- PostgreSQL owns financial facts, revisions, and their provenance (ADR-001).

-- Governed decimal types: explicit scale and rounding mode metadata for every money or
-- rate kind used by calculations; binary floating point is never authoritative.
CREATE TABLE epm_decimal_type (
    type_name     varchar(32) PRIMARY KEY,
    scale         integer NOT NULL CHECK (scale BETWEEN 0 AND 12),
    rounding_mode varchar(16) NOT NULL,
    description   varchar(256) NOT NULL
);

INSERT INTO epm_decimal_type (type_name, scale, rounding_mode, description) VALUES
    ('MONEY', 2, 'HALF_UP', 'Monetary amounts with currency metadata'),
    ('FX_RATE', 4, 'HALF_UP', 'Foreign exchange rates'),
    ('RATIO', 6, 'HALF_UP', 'Covenant and KPI ratios'),
    ('PERCENT', 4, 'HALF_UP', 'Allocation and variance percentages');

-- Registry of governed object identities: one row per stable business object.
CREATE TABLE epm_object (
    object_type    varchar(64)  NOT NULL,
    stable_id      varchar(128) NOT NULL,
    created_at_utc timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_epm_object PRIMARY KEY (object_type, stable_id)
);

-- Append-only revision history: a fact is a (object_type, stable_id, revision) triple.
-- recorded_at_utc is always the receiving time in UTC; effective_date carries the
-- business timebase and stays distinct by design.
CREATE TABLE epm_revision (
    object_type     varchar(64)  NOT NULL,
    stable_id       varchar(128) NOT NULL,
    revision        integer      NOT NULL,
    effective_date  date,
    recorded_at_utc timestamptz  NOT NULL DEFAULT now(),
    actor           varchar(128) NOT NULL,
    reason          varchar(512),
    correlation_id  varchar(128),
    payload         jsonb        NOT NULL,
    payload_hash    char(64)     NOT NULL,
    CONSTRAINT pk_epm_revision PRIMARY KEY (object_type, stable_id, revision),
    CONSTRAINT fk_revision_object FOREIGN KEY (object_type, stable_id)
        REFERENCES epm_object (object_type, stable_id),
    CONSTRAINT ck_revision_positive CHECK (revision > 0)
);

CREATE INDEX ix_revision_recorded ON epm_revision (recorded_at_utc);
CREATE INDEX ix_revision_correlation ON epm_revision (correlation_id);
