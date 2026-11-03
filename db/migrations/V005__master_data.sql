-- Task 9: effective-dated master data and hierarchies.
-- (Renumbered from the plan's V004: V003/V004 were consumed by Tasks 6/8.)

CREATE TABLE epm_entity_version (
    entity_id      varchar(64),
    entity_name    varchar(128) NOT NULL,
    effective_from date NOT NULL,
    effective_to   date,
    currency_code  varchar(3)  NOT NULL,
    region         varchar(32),
    restricted     boolean      NOT NULL DEFAULT false,
    CONSTRAINT ck_entity_interval CHECK (effective_to IS NULL OR effective_to > effective_from),
    PRIMARY KEY (entity_id, effective_from)
);

CREATE TABLE epm_hierarchy_version (
    hierarchy_id   varchar(32) PRIMARY KEY,
    description    varchar(256) NOT NULL,
    effective_from date NOT NULL,
    effective_to   date,
    activated_by   varchar(128) NOT NULL,
    activated_at_utc timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_hierarchy_interval CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE TABLE epm_hierarchy_node (
    hierarchy_id   varchar(32),
    entity_id      varchar(64),
    parent_entity_id varchar(64),
    effective_from date NOT NULL,
    effective_to   date,
    PRIMARY KEY (hierarchy_id, entity_id, effective_from),
    CONSTRAINT ck_node_interval CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT ck_no_self_parent CHECK (parent_entity_id IS NULL OR parent_entity_id <> entity_id)
);

-- Exactly one active hierarchy may resolve a scope at a given date: enforced by an
-- exclusion constraint on overlapping active versions per scope name.
CREATE TABLE epm_scope_activation (
    scope_name     varchar(64),
    hierarchy_id   varchar(32),
    effective_from date NOT NULL,
    effective_to   date,
    PRIMARY KEY (scope_name, hierarchy_id, effective_from),
    CONSTRAINT ck_scope_interval CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX ix_hierarchy_node_interval ON epm_hierarchy_node (hierarchy_id, entity_id, effective_from);

-- Cycle guard: rejection of cyclic assignments happens in the service (boundary test);
-- this trigger additionally refuses a row that would directly close a 2-cycle.
CREATE OR REPLACE FUNCTION epm_reject_direct_cycle()
RETURNS trigger AS $$
BEGIN
    IF NEW.parent_entity_id IS NOT NULL THEN
        IF EXISTS (
            SELECT 1 FROM epm_hierarchy_node reverse
            WHERE reverse.hierarchy_id = NEW.hierarchy_id
              AND reverse.entity_id = NEW.parent_entity_id
              AND reverse.parent_entity_id = NEW.entity_id
        ) THEN
            RAISE EXCEPTION 'cyclic hierarchy assignment rejected';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_hierarchy_no_direct_cycle
BEFORE INSERT OR UPDATE ON epm_hierarchy_node
FOR EACH ROW EXECUTE FUNCTION epm_reject_direct_cycle();
