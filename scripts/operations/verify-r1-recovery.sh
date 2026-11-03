#!/usr/bin/env bash
# R1 recovery verification: restart/replay/rebuild drills that gate G1.
# Runs the AGE rebuild + watermark suite and the migration control suite; the
# platform restart drill is the R0 procedure re-executed on the live cluster.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

echo "[r1-recovery] AGE projection rebuild + watermark gate"
if ! ./mvnw -q -pl services/finance-reporting -Dtest='AgeRebuildTest' test; then
  echo "FAIL: AGE rebuild drill failed" >&2
  exit 1
fi

echo "[r1-recovery] outbox replay + certification recovery"
if ! ./mvnw -q -pl services/finance-core -Dtest='OutboxConcurrencyTest,CertificationControlTest' test; then
  echo "FAIL: replay/certification recovery failed" >&2
  exit 1
fi

echo "[r1-recovery] migration fallback discipline"
if ! ./scripts/operations/verify-migration.sh >/dev/null; then
  echo "FAIL: migration controls failed" >&2
  exit 1
fi

echo "PASS: R1 recovery drills green (AGE rebuild, outbox replay, certification, migration fallback)"
