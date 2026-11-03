-- Task 40: shared planning dimensions and governed multi-currency FX rules.
-- (Renumbered from the plan's V026: V025/V026 are used by Tasks 33/36 and V027 by Task 39.)

CREATE TABLE epm_planning_dimension (
    dimension_id    varchar(64)  PRIMARY KEY,
    name            varchar(200) NOT NULL,
    dimension_type  varchar(32)  NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    owner           varchar(128) NOT NULL,
    created_at_utc  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_dimension_status CHECK (status IN ('ACTIVE', 'RETIRED'))
);

CREATE TABLE epm_dimension_member (
    dimension_id     varchar(64)  NOT NULL,
    member_id        varchar(64)  NOT NULL,
    label            varchar(200) NOT NULL,
    parent_member_id varchar(64),
    effective_from   date         NOT NULL,
    effective_to     date,
    status           varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (dimension_id, member_id, effective_from),
    CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE', 'RETIRED'))
);

CREATE TABLE epm_currency_definition (
    currency_code  char(3)     PRIMARY KEY,
    currency_role  varchar(16) NOT NULL,
    minor_unit_scale integer   NOT NULL DEFAULT 2,
    CONSTRAINT ck_currency_role CHECK (currency_role IN ('TRANSACTION', 'LOCAL', 'GROUP'))
);

CREATE TABLE epm_fx_version (
    fx_version_id  varchar(64)  PRIMARY KEY,
    description    varchar(200) NOT NULL,
    rate_type      varchar(32)  NOT NULL,
    status         varchar(16)  NOT NULL,
    approved_by    varchar(128),
    approved_at_utc timestamptz,
    rounding_mode  varchar(16)  NOT NULL DEFAULT 'HALF_UP',
    output_scale   integer      NOT NULL DEFAULT 2,
    created_at_utc timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_fx_status CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED')),
    CONSTRAINT ck_fx_rate_type CHECK (rate_type IN ('CLOSING', 'AVERAGE', 'BUDGET')),
    CONSTRAINT ck_fx_rounding CHECK (rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN'))
);

CREATE TABLE epm_fx_rate (
    fx_version_id  varchar(64)   NOT NULL,
    from_currency  char(3)       NOT NULL,
    to_currency    char(3)       NOT NULL,
    effective_date date          NOT NULL,
    rate           numeric(24,10) NOT NULL,
    PRIMARY KEY (fx_version_id, from_currency, to_currency, effective_date),
    CONSTRAINT ck_fx_rate_positive CHECK (rate > 0)
);
