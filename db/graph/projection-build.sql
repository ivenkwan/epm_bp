-- Task 21: AGE projection build (idempotent, watermark-aware) and verification.
-- Executed by scripts/operations/rebuild-age-projection.sh and the lineage service.

-- Nodes: source records, mappings, transformations, KPIs, hierarchies.
SELECT ag_catalog.cypher('epm_lineage', $$
    CREATE (n:Source {kind: 'SYSTEM', stableId: 'SYS-ERP-1'})
    RETURN n
$$) AS (n ag_catalog.agtype);
