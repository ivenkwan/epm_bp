-- Task 17: scheduled source ingestion with replay safety.
-- (Renumbered from the plan's V009: V003-V009 were consumed by Tasks 6-14.)

CREATE TABLE epm_ingestion_schedule (
    schedule_id     varchar(64) PRIMARY KEY,
    contract_id     varchar(64)  NOT NULL,
    system_id       varchar(64)  NOT NULL,
    cron            varchar(32)  NOT NULL,
    enabled         boolean      NOT NULL DEFAULT true,
    created_at_utc  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE epm_ingestion_run (
    run_id          uuid PRIMARY KEY,
    schedule_id     varchar(64)  NOT NULL,
    contract_id     varchar(64)  NOT NULL,
    system_id       varchar(64)  NOT NULL,
    batch_identity  varchar(128) NOT NULL,
    status          varchar(40)  NOT NULL,
    row_count       integer      NOT NULL DEFAULT 0,
    retry_count     integer      NOT NULL DEFAULT 0,
    source_freshness timestamptz,
    load_id         uuid,
    started_at_utc  timestamptz  NOT NULL DEFAULT now(),
    finished_at_utc timestamptz,
    initiated_by    varchar(128) NOT NULL,
    correlation_id  varchar(128),
    -- Replay safety: one run per (schedule, batch identity); operator replays return
    -- the original run instead of executing a second time.
    CONSTRAINT uq_ingestion_batch UNIQUE (schedule_id, batch_identity),
    CONSTRAINT ck_ingestion_status CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'ZERO_ROWS_COMPLETENESS_EXCEPTION', 'FAILED', 'SKIPPED_DISABLED')
    )
);

CREATE INDEX ix_ingestion_run_schedule ON epm_ingestion_run (schedule_id, started_at_utc);
