-- Task 39: enterprise/division management reports and initial decision packs.
-- (Renumbered from the plan's V025: V025/V026 are used by Tasks 33/36.)

CREATE TABLE epm_management_report (
    report_id      varchar(64)   PRIMARY KEY,
    report_scope   varchar(16)   NOT NULL,
    division_id    varchar(64),
    entity_id      varchar(64),
    title          varchar(200)  NOT NULL,
    layout         varchar(32)   NOT NULL,
    status         varchar(16)   NOT NULL,
    owner          varchar(128)  NOT NULL,
    report_version integer       NOT NULL,
    created_at_utc timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_report_scope CHECK (report_scope IN ('ENTERPRISE', 'DIVISION', 'ENTITY')),
    CONSTRAINT ck_report_layout CHECK (layout IN ('FLASH', 'OPERATIONAL', 'BOARD')),
    CONSTRAINT ck_report_status CHECK (status IN ('DRAFT', 'CERTIFIED', 'RETIRED')),
    CONSTRAINT ck_scope_targets CHECK (
        (report_scope = 'ENTERPRISE' AND division_id IS NULL AND entity_id IS NULL)
        OR (report_scope = 'DIVISION' AND division_id IS NOT NULL AND entity_id IS NULL)
        OR (report_scope = 'ENTITY' AND division_id IS NOT NULL AND entity_id IS NOT NULL))
);

CREATE TABLE epm_report_section (
    report_id    varchar(64)  NOT NULL,
    section_id   varchar(64)  NOT NULL,
    title        varchar(200) NOT NULL,
    required     boolean      NOT NULL DEFAULT true,
    data_product varchar(64)  NOT NULL,
    comparison   varchar(32),
    PRIMARY KEY (report_id, section_id),
    CONSTRAINT ck_section_comparison CHECK (
        comparison IN ('PRIOR_PERIOD', 'BUDGET', 'FORECAST', NULL))
);

CREATE TABLE epm_report_figure (
    report_id     varchar(64)   NOT NULL,
    section_id    varchar(64)   NOT NULL,
    figure_id     varchar(64)   NOT NULL,
    kpi_id        varchar(64)   NOT NULL,
    amount        numeric(24,6),
    activity_state varchar(16)  NOT NULL,
    PRIMARY KEY (report_id, section_id, figure_id),
    CONSTRAINT ck_figure_activity CHECK (
        activity_state IN ('ZERO_ACTIVITY', 'MISSING', 'VALUE'))
);

CREATE TABLE epm_decision_pack (
    pack_id        varchar(64)  PRIMARY KEY,
    pack_layout    varchar(32)  NOT NULL,
    title          varchar(200) NOT NULL,
    status         varchar(16)  NOT NULL,
    owner          varchar(128) NOT NULL,
    edition_number integer      NOT NULL,
    created_at_utc timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_pack_layout CHECK (pack_layout IN ('FLASH', 'OPERATIONAL', 'BOARD')),
    CONSTRAINT ck_pack_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'RETIRED'))
);

CREATE TABLE epm_pack_member (
    pack_id   varchar(64) NOT NULL,
    report_id varchar(64) NOT NULL,
    required  boolean     NOT NULL DEFAULT true,
    PRIMARY KEY (pack_id, report_id)
);

CREATE TABLE epm_pack_edition (
    pack_id         varchar(64)  NOT NULL,
    edition_number  integer      NOT NULL,
    content_hash    varchar(128) NOT NULL,
    as_of_utc       timestamptz  NOT NULL,
    generated_by    varchar(128) NOT NULL,
    opening_balance numeric(24,6),
    prior_closing   numeric(24,6),
    PRIMARY KEY (pack_id, edition_number)
);

CREATE TABLE epm_pack_approval (
    approval_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pack_id        varchar(64)  NOT NULL,
    edition_number integer      NOT NULL,
    approver       varchar(128) NOT NULL,
    outcome        varchar(16)  NOT NULL,
    comment        varchar(2000),
    decided_at_utc timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_approval_outcome CHECK (outcome IN ('APPROVED', 'REJECTED'))
);
