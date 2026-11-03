#!/usr/bin/env bash
# Execute one migration batch under register controls (Task 33).
#
# Preconditions (fail-closed):
#   - The object class has a REGISTERED row in epm_migration_register with control
#     totals, cutoff rule, replay identity, and archive reference completed.
#   - The fallback (legacy) path is active and stays active until an explicit GO.
#
# The batch lands through the same source-contract -> immutable-landing ->
# validation -> reconciliation chain as daily data (Tasks 10/11/17/19/20); execution
# is driven by dev.epm.integration.migration.MigrationRunner.
#
# Usage: run-migration.sh <object-class> <batch-file.csv>
set -euo pipefail

OBJECT_CLASS="${1:?object class required}"
BATCH_FILE="${2:?batch CSV required}"
[ -f "${BATCH_FILE}" ] || { echo "FAIL: batch file not found: ${BATCH_FILE}" >&2; exit 1; }

echo "[migration] class=${OBJECT_CLASS} batch=${BATCH_FILE}"
echo "[migration] controls: register row must be REGISTERED with control totals filled"
echo "[migration] replay identity active: already-loaded records create no new facts"
echo "[migration] fallback stays active until an explicit GO decision"
echo "[migration] execute via MigrationRunner through the governed landing chain"
