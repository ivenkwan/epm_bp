-- Task 48: approved drivers and isolated what-if scenarios.
-- (Renumbered from the plan's V031: V027-V032 are used by Tasks 39-44.)

CREATE TABLE epm_driver (
    driver_id     varchar(64)  PRIMARY KEY,
    name          varchar(200) NOT NULL,
    formula       varchar(500) NOT NULL,
    formula_version varchar(32) NOT NULL,
    min_value     numeric(24,6) NOT NULL,
    max_value     numeric(24,6) NOT NULL,
    depends_on    varchar(256),
    owner         varchar(128) NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'APPROVED',
    worked_example varchar(500),
    CONSTRAINT ck_driver_status CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED')),
    CONSTRAINT ck_driver_range CHECK (max_value >= min_value)
);

CREATE TABLE epm_scenario (
    scenario_id   varchar(64)  PRIMARY KEY,
    baseline_ref  varchar(128) NOT NULL,
    name          varchar(200) NOT NULL,
    status        varchar(16)  NOT NULL DEFAULT 'DRAFT',
    promoted_to   varchar(128),
    created_by    varchar(128) NOT NULL,
    created_at_utc timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_scenario_status CHECK (status IN ('DRAFT', 'VALID', 'INVALID', 'PROMOTED'))
);

CREATE TABLE epm_scenario_override (
    scenario_id varchar(64)  NOT NULL,
    driver_id   varchar(64)  NOT NULL,
    override_kind varchar(16) NOT NULL,
    override_value numeric(24,6) NOT NULL,
    applied_at_utc timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (scenario_id, driver_id, override_kind),
    CONSTRAINT ck_override_kind CHECK (override_kind IN ('VOLUME', 'PRICE', 'FX'))
);

CREATE TABLE epm_scenario_result (
    scenario_id varchar(64)  NOT NULL,
    line_key    varchar(128) NOT NULL,
    amount      numeric(24,6) NOT NULL,
    trace       varchar(1000) NOT NULL,
    PRIMARY KEY (scenario_id, line_key)
);
