-- Task 11: immutable landing, load logs, and audit retrieval.
-- (Renumbered from the plan's V006: V003-V006 were consumed by Tasks 6-10.)

-- Append-only original payload storage. Original bytes are never overwritten; duplicate
-- payload content is content-addressed while different submission identities remain
-- distinguishable through the load log.
CREATE TABLE epm_landed_payload (
    payload_id     uuid PRIMARY KEY,
    content_hash   char(64)     NOT NULL,
    content        bytea        NOT NULL,
    content_type   varchar(64)  NOT NULL,
    byte_size      bigint       NOT NULL,
    landed_at_utc  timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX ix_landed_payload_hash ON epm_landed_payload (content_hash);

-- Immutable load log: one row per submission landing, linked to the payload, the
-- contract version in force, and the revision created downstream.
CREATE TABLE epm_load_log (
    load_id            uuid PRIMARY KEY,
    source_system_id   varchar(64)  NOT NULL,
    contract_id        varchar(64)  NOT NULL,
    contract_version   integer      NOT NULL,
    submission_id      varchar(128) NOT NULL,
    revision_id        bigint,
    payload_id         uuid         NOT NULL REFERENCES epm_landed_payload (payload_id),
    run_id             varchar(128) NOT NULL,
    row_count          integer      NOT NULL,
    source_timestamp   timestamptz,
    receiving_timestamp timestamptz NOT NULL DEFAULT now(),
    outcome            varchar(16)  NOT NULL,
    CONSTRAINT ck_load_outcome CHECK (outcome IN ('LANDED', 'DUPLICATE_REVIEW', 'REJECTED'))
);

CREATE INDEX ix_load_log_submission ON epm_load_log (submission_id);
CREATE INDEX ix_load_log_run ON epm_load_log (run_id);

-- Immutability: same guard discipline as audit evidence. The application role cannot
-- UPDATE or DELETE landing evidence (Task 5 grants pattern covers new tables via
-- default privileges; this trigger protects privileged actors too).
CREATE OR REPLACE FUNCTION epm_guard_landing_evidence()
RETURNS trigger AS $$
BEGIN
    INSERT INTO epm_audit_violation (audit_id, attempted_action)
    VALUES (NULL, TG_OP);
    RETURN NULL; -- skip the mutation; original bytes and outcomes never change
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_landed_payload_immutable
BEFORE UPDATE OR DELETE ON epm_landed_payload
FOR EACH ROW EXECUTE FUNCTION epm_guard_landing_evidence();

CREATE TRIGGER trg_load_log_immutable
BEFORE UPDATE OR DELETE ON epm_load_log
FOR EACH ROW EXECUTE FUNCTION epm_guard_landing_evidence();
