#!/usr/bin/env bash
# Task 56 final recovery verification: every operational recovery gate across R0-R4
# plus the final adversarial and workload suites. Fails closed on any failure.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

fail() { echo "FAIL: $1" >&2; exit 1; }

echo "[final-recovery] R1 recovery drills (AGE rebuild, outbox replay, certification, fallback)"
./scripts/operations/verify-r1-recovery.sh >/dev/null || fail "R1 recovery gate failed"

echo "[final-recovery] R2 treasury cutover verification"
./scripts/operations/cutover-treasury.sh verify >/dev/null || fail "cutover verification failed"

echo "[final-recovery] planning migration controls (totals, cutoff, replay, reconciliation, fallback)"
./scripts/operations/run-planning-migration.sh --verify >/dev/null \
  || fail "planning migration gate failed"

echo "[final-recovery] AI security suite (routes disabled, no credential literals)"
./scripts/quality/verify-security.sh --suite ai >/dev/null || fail "AI security gate failed"

echo "[final-recovery] final adversarial denial suite (F-043/F-044 + AI authority)"
./mvnw -q -pl tests/integration -Dtest='FinalAdversarialTest' test \
  || fail "final adversarial suite failed"

echo "[final-recovery] final structural workloads (integrated model, governed drill)"
./mvnw -q -pl tests/performance -Dtest='FinalAcceptanceWorkloadTest' test \
  || fail "final workload suite failed"

echo "PASS: final recovery verification (R1 recovery, cutover, planning migration, AI security, adversarial, workloads)"
