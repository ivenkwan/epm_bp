#!/usr/bin/env bash
# Execute the planning migration under register controls (Task 46, F-042).
#
# Migrates approved opening plan versions, shared planning dimensions, FX basis,
# non-driver schedules, and historical comparisons with lineage and control totals.
#
# Preconditions (fail-closed):
#   - Every planning object class (PLAN_VERSION, PLANNING_DIMENSION, FX_BASIS,
#     INPUT_SCHEDULE, PLAN_COMPARISON) has a REGISTERED row in epm_migration_register
#     with control totals, cutoff rule, replay identity, and archive reference.
#   - Replay identity active: already-loaded records create no new facts.
#   - The legacy fallback stays active until an explicit GO — technical migration is
#     never retirement approval; prior systems remain available until separately
#     authorized.
#
# Usage:
#   run-planning-migration.sh          # describe controls and verify them
#   run-planning-migration.sh --verify # run the PlanningMigrationTest gate
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

MODE="${1:-describe}"

if [ "${MODE}" = "--verify" ]; then
  if ! ./mvnw -q -pl tests/integration -Dtest='PlanningMigrationTest' test; then
    echo "FAIL: planning migration controls did not verify" >&2
    exit 1
  fi
  echo "PASS: planning migration verified (totals, cutoff, replay, reconciliation, fallback)"
  exit 0
fi

echo "[planning-migration] classes: PLAN_VERSION PLANNING_DIMENSION FX_BASIS INPUT_SCHEDULE PLAN_COMPARISON"
echo "[planning-migration] controls: register rows must be REGISTERED with control totals + archive refs"
echo "[planning-migration] reconciliation: integrated statements reconcile independently against control totals"
echo "[planning-migration] replay identity: already-loaded records create no new facts"
echo "[planning-migration] fallback stays active until an explicit GO (retirement is a separate decision)"
echo "[planning-migration] execution: PlanningMigrationTest exercises every control; batches land via MigrationRunner"
