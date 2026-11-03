-- Task 23: certified cash position storage.
-- (Renumbered from the plan's V014: V003-V015 were consumed by Tasks 6-22.)

CREATE TABLE epm_cash_position (
    account_id      varchar(64),
    business_date   date,
    entity_id       varchar(64)  NOT NULL,
    currency        varchar(3)   NOT NULL,
    opening         numeric(24,6) NOT NULL,
    inflows         numeric(24,6) NOT NULL,
    outflows        numeric(24,6) NOT NULL,
    closing         numeric(24,6) NOT NULL,
    source_revision varchar(64)  NOT NULL,
    fx_version      varchar(32)  NOT NULL,
    prior_close     numeric(24,6),
    certified       boolean      NOT NULL DEFAULT false,
    calc_version    varchar(32)  NOT NULL DEFAULT 'cash-1.0.0',
    calculated_at_utc timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, business_date),
    CONSTRAINT ck_cash_scale CHECK (
        opening = round(opening, 2) AND inflows = round(inflows, 2)
        AND outflows = round(outflows, 2) AND closing = round(closing, 2))
);

-- Group-level certified rollup with FX basis and continuity evidence.
CREATE TABLE epm_cash_position_group (
    scope           varchar(16)  PRIMARY KEY,
    business_date   date         NOT NULL,
    currency        varchar(3)   NOT NULL,
    opening_total   numeric(24,6) NOT NULL,
    inflow_total    numeric(24,6) NOT NULL,
    outflow_total   numeric(24,6) NOT NULL,
    closing_total   numeric(24,6) NOT NULL,
    fx_version      varchar(32)  NOT NULL,
    rounding_mode   varchar(8)   NOT NULL DEFAULT 'HALF_UP',
    scale           integer      NOT NULL DEFAULT 2,
    calculated_at_utc timestamptz NOT NULL DEFAULT now()
);
