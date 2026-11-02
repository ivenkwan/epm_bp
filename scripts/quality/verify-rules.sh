#!/usr/bin/env bash
# Rules governance gate. Full coverage (100% rule/DMN row + boundary fixtures, deterministic
# explanations, bounded termination, zero side effects) lands with Task 13; until rule assets
# exist this gate passes vacuously so CI never reports an unearned green light.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RULES_DIR="${REPO_ROOT}/rules"

if [ ! -d "${RULES_DIR}" ]; then
  echo "PASS (vacuous): no rules/ directory yet; governance gate activates with Task 13 assets"
  exit 0
fi

ASSETS="$(find "${RULES_DIR}" -type f \( -name '*.drl' -o -name '*.dmn' \) | wc -l)"
if [ "${ASSETS}" -eq 0 ]; then
  echo "PASS (vacuous): no DRL/DMN assets under rules/ yet; governance gate activates with Task 13 assets"
  exit 0
fi

# Rule assets exist: require the governance suite to run.
if ! ./mvnw -q -pl tests/integration -Dtest='RuleGovernanceTest,ValidationBoundaryTest,CovenantBoundaryTest' test; then
  echo "FAIL: rule assets present but governance suite did not pass" >&2
  exit 1
fi

echo "PASS: rules governance suite green"
