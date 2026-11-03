#!/usr/bin/env bash
# Verify a restored environment end to end (Task 37): AGE installed, schema restored,
# watermarks consistent, and no duplicate business effects. Exits non-zero on any
# critical integrity failure.
# Usage: verify-restore.sh [--release R2]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

PGHOST="${EPM_PG_HOST:?EPM_PG_HOST required}"
PGPORT="${EPM_PG_PORT:-5432}"
PGDATABASE="${EPM_PG_DATABASE:-epm}"
PGUSER="${EPM_PG_USER:?EPM_PG_USER required}"
export PGHOST PGPORT PGDATABASE PGUSER

echo "[verify-restore] AGE extension installed on the target"
PGPASSWORD="${EPM_PG_PASSWORD:?}" psql -tAc \
  "SELECT 1 FROM pg_extension WHERE extname = 'age'" | grep -q 1 \
  || { echo "FAIL: AGE not installed on restore target" >&2; exit 1; }

echo "[verify-restore] governed schema present"
TABLES="$(PGPASSWORD="${EPM_PG_PASSWORD:?}" psql -tAc \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public')"
[ "${TABLES}" -ge 30 ] \
  || { echo "FAIL: expected >=30 public tables, found ${TABLES}" >&2; exit 1; }

echo "[verify-restore] no duplicate business effects (one certification per revision)"
DUPES="$(PGPASSWORD="${EPM_PG_PASSWORD:?}" psql -tAc \
  "SELECT count(*) FROM (
     SELECT object_type, stable_id, revision FROM epm_certification
     GROUP BY 1,2,3 HAVING count(*) > 1
   ) d")"
[ "${DUPES}" = "0" ] \
  || { echo "FAIL: ${DUPES} duplicated certification revisions" >&2; exit 1; }

echo "[verify-restore] recovery suites (outbox replay, certification, AGE watermark)"
./mvnw -q -pl services/finance-core -Dtest='OutboxConcurrencyTest,CertificationControlTest' test
./mvnw -q -pl services/finance-reporting -Dtest='AgeRebuildTest' test

echo "PASS: restore verified (AGE, schema, watermarks, no duplicate effects)"
