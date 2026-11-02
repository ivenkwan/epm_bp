-- Task 6: API safety persistence - idempotency and durable jobs.

-- Idempotency records bind a client key to (actor, operation, canonical payload
-- fingerprint) and store the original response. Replay returns the stored response;
-- the same key with a different fingerprint is a 409. Written in the same transaction
-- as the business effect, so a rolled-back request never claims a key.
CREATE TABLE epm_idempotency (
    idempotency_key  varchar(128) PRIMARY KEY,
    actor            varchar(128) NOT NULL,
    operation        varchar(128) NOT NULL,
    fingerprint      char(64)     NOT NULL,
    response_status  integer      NOT NULL,
    response_body    text         NOT NULL,
    correlation_id   varchar(128),
    created_at_utc   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_idem_response CHECK (response_status BETWEEN 200 AND 299)
);

-- Durable jobs: long work is a first-class resource with status, progress, attempts,
-- and a result. The (job_type, body digest) uniqueness plus the idempotency row above
-- guarantee a replayed submission never creates or executes a second job.
CREATE TABLE epm_job (
    job_id            uuid PRIMARY KEY,
    job_type          varchar(64)  NOT NULL,
    request_body      text         NOT NULL,
    body_digest       char(64)     NOT NULL,
    status            varchar(16)  NOT NULL DEFAULT 'QUEUED',
    progress_percent  integer      NOT NULL DEFAULT 0,
    execution_count   integer      NOT NULL DEFAULT 0,
    submitted_by      varchar(128) NOT NULL,
    correlation_id    varchar(128),
    result            text,
    submitted_at_utc  timestamptz  NOT NULL DEFAULT now(),
    updated_at_utc    timestamptz  NOT NULL DEFAULT now(),
    finished_at_utc   timestamptz,
    CONSTRAINT ck_job_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_job_progress CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT uq_job_execution UNIQUE (job_type, body_digest)
);

CREATE INDEX ix_job_status ON epm_job (status, submitted_at_utc);
