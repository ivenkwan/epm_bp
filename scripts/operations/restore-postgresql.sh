#!/usr/bin/env bash
# Restore a PostgreSQL backup into an isolated target, installing AGE consistently
# (Task 37). Credentials from the environment only.
# Usage: restore-postgresql.sh <backup-file> [--schema|--full]
set -euo pipefail

BACKUP_FILE="${1:?backup file required}"
MODE="${2:---schema}"
[ -f "${BACKUP_FILE}" ] || { echo "FAIL: backup file not found: ${BACKUP_FILE}" >&2; exit 1; }

PGHOST="${EPM_PG_HOST:?EPM_PG_HOST required}"
PGPORT="${EPM_PG_PORT:-5432}"
PGDATABASE="${EPM_PG_DATABASE:-epm}"
PGUSER="${EPM_PG_USER:?EPM_PG_USER required}"
export PGHOST PGPORT PGDATABASE PGUSER

# AGE must exist on the restore target before any graph objects are touched.
PGPASSWORD="${EPM_PG_PASSWORD:?}" psql -v ON_ERROR_STOP=1 -c "CREATE EXTENSION IF NOT EXISTS age" "${PGDATABASE}"

if [ "${MODE}" = "--schema" ]; then
  PGPASSWORD="${EPM_PG_PASSWORD:?}" psql -v ON_ERROR_STOP=1 -f "${BACKUP_FILE}" "${PGDATABASE}"
else
  PGPASSWORD="${EPM_PG_PASSWORD:?}" pg_restore --clean --if-exists -d "${PGDATABASE}" "${BACKUP_FILE}"
fi

echo "PASS: restore into ${PGDATABASE}@${PGHOST}:${PGPORT} complete with AGE installed"
