-- Task 5: immutable audit and transactional outbox.

-- Append-only audit evidence with full old/new values and approval linkage.
CREATE TABLE epm_audit_event (
    audit_id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_type      varchar(64)  NOT NULL,
    stable_id        varchar(128) NOT NULL,
    revision         integer      NOT NULL,
    actor            varchar(128) NOT NULL,
    action           varchar(64)  NOT NULL,
    reason           varchar(512),
    correlation_id   varchar(128),
    old_values        jsonb,
    new_values        jsonb,
    approvals        jsonb,
    recorded_at_utc  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_action CHECK (action IN ('CREATE', 'UPDATE', 'APPROVE', 'CERTIFY', 'REJECT', 'PUBLISH', 'REVERSE', 'ADJUST'))
);

CREATE INDEX ix_audit_object ON epm_audit_event (object_type, stable_id, revision);
CREATE INDEX ix_audit_correlation ON epm_audit_event (correlation_id);

-- Security audit path for attempted evidence tampering: filled by the guard trigger
-- below whenever a privileged actor tries to UPDATE or DELETE audit evidence.
CREATE TABLE epm_audit_violation (
    violation_id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_id          bigint,
    attempted_action  varchar(16) NOT NULL,
    attempt_actor     text        NOT NULL DEFAULT current_user,
    attempted_at_utc  timestamptz NOT NULL DEFAULT now()
);

-- Guard trigger: for any role that still holds UPDATE/DELETE privileges (e.g. the
-- migration/owner role), record the attempt and silently skip the mutation so the
-- evidence row itself never changes. The application role has those privileges revoked
-- outright (least privilege denial happens before PostgreSQL even runs the trigger).
CREATE OR REPLACE FUNCTION epm_guard_audit_evidence()
RETURNS trigger AS $$
BEGIN
    INSERT INTO epm_audit_violation (audit_id, attempted_action)
    VALUES (COALESCE(OLD.audit_id, NEW.audit_id), TG_OP);
    RETURN NULL; -- skip the row mutation; evidence stays byte-identical
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_immutable
BEFORE UPDATE OR DELETE ON epm_audit_event
FOR EACH ROW EXECUTE FUNCTION epm_guard_audit_evidence();

-- Transactional outbox: exactly one row per committed business effect. The event id is
-- stable across redeliveries so consumers deduplicate; consumers fetch authoritative
-- state through APIs (they never trust payload as the system of record).
CREATE TABLE epm_outbox (
    event_id          uuid PRIMARY KEY,
    aggregate_type    varchar(64)  NOT NULL,
    aggregate_id      varchar(128) NOT NULL,
    event_type        varchar(64)  NOT NULL,
    payload           jsonb        NOT NULL,
    correlation_id    varchar(128),
    occurred_at_utc   timestamptz  NOT NULL DEFAULT now(),
    dispatched_at_utc timestamptz,
    dispatch_attempts integer      NOT NULL DEFAULT 0,
    last_error        varchar(512)
);

CREATE INDEX ix_outbox_pending ON epm_outbox (occurred_at_utc)
    WHERE dispatched_at_utc IS NULL;

-- Least-privilege application role: the application may read and append evidence but
-- never rewrite or delete it. The role has no login (sessions assume it explicitly or
-- via SET ROLE from a supervised connection), so no credential is embedded here.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'epm_app') THEN
        CREATE ROLE epm_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO epm_app;
GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA public TO epm_app;
REVOKE UPDATE, DELETE ON ALL TABLES IN SCHEMA public FROM epm_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE UPDATE, DELETE ON TABLES FROM epm_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT ON TABLES TO epm_app;
