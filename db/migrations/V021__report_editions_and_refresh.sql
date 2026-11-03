-- Task 28: report editions, refresh, commentary, and controlled republication.
-- (Renumbered from the plan's V019: V003-V016 were consumed by Tasks 6-23, and
-- V017-V022 are used by Tasks 24-29 as planned.)

CREATE TABLE epm_report_edition (
    edition_id       varchar(64) PRIMARY KEY,
    report_id        varchar(64)  NOT NULL,
    edition_number   integer      NOT NULL,
    object_revision  integer      NOT NULL,
    status           varchar(16)  NOT NULL,
    prepared_by      varchar(128) NOT NULL,
    approved_by      varchar(128),
    published_by     varchar(128),
    narrative        text,
    published_at_utc timestamptz,
    created_at_utc   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_edition_status CHECK (status IN ('DRAFT', 'REJECTED', 'PUBLISHED', 'SUPERSEDED', 'REVOKED')),
    CONSTRAINT uq_edition_number UNIQUE (report_id, edition_number)
);

CREATE TABLE epm_publication_comment (
    comment_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edition_id  varchar(64)  NOT NULL,
    author      varchar(128) NOT NULL,
    body        varchar(2000) NOT NULL,
    resolution  varchar(16)  NOT NULL DEFAULT 'OPEN',
    resolved_by varchar(128),
    created_at_utc timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_comment_resolution CHECK (resolution IN ('OPEN', 'ADDRESSED', 'REJECTED'))
);
