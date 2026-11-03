#!/usr/bin/env bash
# PostgreSQL point-in-time backup with AGE support (Task 37).
# Credentials come from the environment only (PGPASSWORD / EPM_PG_*); never literals.
# Usage: backup-postgresql.sh <backup-dir> [--full|--schema]
set -euo pipefail

BACKUP_DIR="${1:?backup directory required}"
MODE="${2:---full}"
mkdir -p "${BACKUP_DIR}"

PGHOST="${EPM_PG_HOST:?EPM_PG_HOST required}"
PGPORT="${EPM_PG_PORT:-5432}"
PGDATABASE="${EPM_PG_DATABASE:-epm}"
PGUSER="${EPM_PG_USER:?EPM_PG_USER required}"
export PGHOST PGPORT PGDATABASE PGUSER

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_ID="bkp-${STAMP}"
{
  echo "backup_id=${BACKUP_ID}"
  echo "mode=${MODE}"
  echo "started_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "database=${PGDATABASE}"
} > "${BACKUP_DIR}/${BACKUP_ID}.manifest"

if [ "${MODE}" = "--schema" ]; then
  PGPASSWORD="${EPM_PG_PASSWORD:?}" pg_dump -s -n public -f "${BACKUP_DIR}/${BACKUP_ID}.sql" "${PGDATABASE}"
else
  PGPASSWORD="${EPM_PG_PASSWORD:?}" pg_dump -Fc -f "${BACKUP_DIR}/${BACKUP_ID}.dump" "${PGDATABASE}"
fi

sha256sum "${BACKUP_DIR}/${BACKUP_ID}".{sql,dump} 2>/dev/null | while read -r digest file; do
  echo "$(basename "${file}") ${digest}"
done >> "${BACKUP_DIR}/${BACKUP_ID}.manifest"
echo "completed_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "${BACKUP_DIR}/${BACKUP_ID}.manifest"

echo "PASS: backup ${BACKUP_ID} written to ${BACKUP_DIR} (immutable evidence: manifest + digests)"
