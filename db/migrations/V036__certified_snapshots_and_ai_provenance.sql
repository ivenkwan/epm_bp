-- Task 51: certified snapshots and AI provenance.
-- (Renumbered from the plan's V034: V027-V035 are used by Tasks 39-44, 48-50.)

CREATE TABLE epm_certified_snapshot (
    snapshot_id    varchar(64)  PRIMARY KEY,
    kpi_id         varchar(64)  NOT NULL,
    period         varchar(8)   NOT NULL,
    source_version varchar(64)  NOT NULL,
    reconciled_to  varchar(200) NOT NULL,
    entitled_scope varchar(500) NOT NULL,
    built_at_utc   timestamptz  NOT NULL DEFAULT now(),
    certified      boolean      NOT NULL DEFAULT false
);

CREATE TABLE epm_ai_provenance (
    provenance_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_id       varchar(64)  NOT NULL,
    model_version  varchar(64)  NOT NULL,
    prompt_version varchar(64)  NOT NULL,
    snapshot_id    varchar(64),
    output_ref     varchar(200),
    validation     varchar(200) NOT NULL,
    editor         varchar(128),
    reviewer       varchar(128),
    approver       varchar(128),
    created_at_utc timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE epm_ai_route_state (
    route_id     varchar(64) PRIMARY KEY,
    enabled      boolean      NOT NULL DEFAULT false,
    disabled_by  varchar(128),
    disabled_at_utc timestamptz,
    reason       varchar(500)
);
