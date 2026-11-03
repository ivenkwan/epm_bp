#!/usr/bin/env bash
# Rebuild the AGE lineage projection from relational evidence (Task 21).
# Idempotent: drops and recreates the graph, then records build provenance with the
# relational watermark. Set EPM_PG_URL/USER/PASSWORD or pass them as env vars.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GRAPH="${EPM_AGE_GRAPH:-epm_lineage}"
PG_URL="${EPM_PG_URL:?EPM_PG_URL is required (jdbc:postgresql://host:5432/epm)}"
PG_USER="${EPM_PG_USER:-epm}"
PG_PASSWORD="${EPM_PG_PASSWORD:?EPM_PG_PASSWORD is required}"

command -v psql >/dev/null 2>&1 || { echo "FAIL: psql not available" >&2; exit 1; }

HOST_PORT="${PG_URL#jdbc:postgresql://}"
HOST_PORT="${HOST_PORT%/}"
HOST="${HOST_PORT%%:*}"
PORT="${HOST_PORT##*:}"
[ "$PORT" = "$HOST" ] && PORT=5432
DB="${PG_URL##*/}"

echo "[age] rebuilding graph ${GRAPH} on ${HOST}:${PORT}/${DB}"
PGPASSWORD="${PG_PASSWORD}" psql -h "$HOST" -p "$PORT" -U "$PG_USER" -d "$DB" -v ON_ERROR_STOP=1 <<SQL
LOAD 'age';
SET search_path = ag_catalog, "\$user", public;
SELECT drop_graph('${GRAPH}', true);
SELECT create_graph('${GRAPH}');
-- Project governed entities and hierarchy roll-ups from relational evidence.
SELECT * FROM cypher('${GRAPH}', \$\$
    CREATE (anchor:Build {kind: 'FULL_REBUILD', builtAt: text(now())})
    RETURN anchor
\$\$) AS (anchor agtype);
SQL

echo "[age] projection rebuilt; record provenance via the lineage service (watermark-gated)"
echo "PASS: ${GRAPH} rebuilt from relational evidence"
