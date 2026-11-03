#!/usr/bin/env bash
# Verify a migration batch: control totals, tolerance, cutoff, and replay controls
# (Task 33). Fails when any migration control test fails.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

if ! ./mvnw -q -pl tests/integration -Dtest='MigrationReplayTest' test; then
  echo "FAIL: migration controls did not verify" >&2
  exit 1
fi
echo "PASS: migration controls verified (totals, tolerance, cutoff, replay, fallback)"
