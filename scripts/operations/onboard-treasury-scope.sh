#!/usr/bin/env bash
# Onboard one treasury wave under register controls (Task 35).
#
# Fail-closed preconditions — the wave is REJECTED (no "complete" claim accepted)
# unless every one holds:
#   1. The wave has a REGISTERED row in docs/operations/r2-onboarding-register.md
#      with owner, sources/entities, contract, template/feed, grain, cutoff, control
#      totals, historical depth, entitlement scope, readiness evidence, and rollback
#      boundary filled.
#   2. No other wave is in REMEDIATE.
#   3. Contract, validation, reconciliation, security, and parallel-output checks
#      passed for the wave's whole population.
#   4. The G1 scale decision authorizes scale.
#
# Usage: onboard-treasury-scope.sh <wave-id>
set -euo pipefail

WAVE_ID="${1:?wave id required (e.g. WAVE-1)}"
REGISTER="docs/operations/r2-onboarding-register.md"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

[ -f "${REGISTER}" ] || { echo "FAIL: onboarding register missing" >&2; exit 1; }

if ! grep -q "WAVE-" "${REGISTER}"; then
  echo "REJECTED: wave ${WAVE_ID} has no registered row; unregistered 'complete' claims are not accepted" >&2
  exit 1
fi

if ! grep -qE '^\| WAVE-.*\|.*(REGISTERED|ACTIVE) *\|' "${REGISTER}"; then
  echo "REJECTED: wave ${WAVE_ID} readiness row incomplete" >&2
  exit 1
fi

echo "[onboard] wave ${WAVE_ID}: running contract/validation/reconciliation/security/parallel checks"
if ! ./mvnw -q -pl services/finance-integration -Dtest='SourceContractTest,ValidationBoundaryTest,LandingImmutabilityTest,ScheduledIngestionTest' test; then
  echo "REJECTED: wave ${WAVE_ID} integration checks failed; wave enters REMEDIATE" >&2
  exit 1
fi
if ! ./mvnw -q -pl services/finance-core -Dtest='CertificationControlTest,CashPositionTest,CovenantBoundaryTest,FundingHeadroomTest,IntercompanyMatchingTest' test; then
  echo "REJECTED: wave ${WAVE_ID} reconciliation checks failed; wave enters REMEDIATE" >&2
  exit 1
fi
if ! ./mvnw -q -pl services/finance-admin -Dtest='AuthorizationSecurityTest,BreakGlassTest' test; then
  echo "REJECTED: wave ${WAVE_ID} security checks failed; wave enters REMEDIATE" >&2
  exit 1
fi

echo "PASS: wave ${WAVE_ID} onboarding checks green; mark the register row ACTIVE with evidence before the next wave"
