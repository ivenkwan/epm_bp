-- Task 36: optional stock/unit liquidity context (F-022).
-- (Renumbered from the plan's V024: V024/V025 are used by Tasks 31/33.)

CREATE TABLE epm_stock_unit_position (
    entity_id       varchar(64),
    as_of_date      date,
    item_id         varchar(64),
    quantity        numeric(24,6) NOT NULL,
    ageing_bucket   varchar(16),
    classification  varchar(32),
    movement_ref    varchar(64),
    optional_remark varchar(512),
    source_revision varchar(64)  NOT NULL,
    source_freshness timestamptz NOT NULL,
    certified       boolean      NOT NULL DEFAULT false,
    PRIMARY KEY (entity_id, as_of_date, item_id),
    CONSTRAINT ck_ageing_bucket CHECK (
        ageing_bucket IN ('0_30', '31_60', '61_90', 'OVER_90', NULL))
);
