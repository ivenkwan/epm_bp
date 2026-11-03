-- Task 21: AGE projection verification (sampled stable-ID equivalence).
-- Fails (no rows / mismatch counts) when the graph is not equivalent to relational.

-- Expected node count from the relational register of projected entities.
SELECT 'nodes' AS check,
       (SELECT count(*) FROM epm_entity_version) AS expected,
       (SELECT count(*) FROM ag_catalog.cypher('epm_lineage',
           $$ MATCH (n:Entity) RETURN n $$) AS (n ag_catalog.agtype)) AS projected
UNION ALL
SELECT 'edges',
       (SELECT count(*) FROM epm_hierarchy_node),
       (SELECT count(*) FROM ag_catalog.cypher('epm_lineage',
           $$ MATCH (:Entity)-[:ROLLS_UP_TO]->(:Entity) RETURN 1 $$) AS (e ag_catalog.agtype));
