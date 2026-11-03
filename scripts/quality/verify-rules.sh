#!/usr/bin/env bash
# Rules governance gate (Task 13): 100% row/fixture coverage over committed DRL/DMN
# assets, deterministic explanations, bounded termination, and zero unauthorized effects.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RULES_DIR="${REPO_ROOT}/rules"

if [ ! -d "${RULES_DIR}" ]; then
  echo "FAIL: rules/ directory missing" >&2
  exit 1
fi

ASSETS="$(find "${RULES_DIR}" -type f \( -name '*.drl' -o -name '*.dmn' \) | wc -l)"
if [ "${ASSETS}" -eq 0 ]; then
  echo "FAIL: no DRL/DMN assets under rules/" >&2
  exit 1
fi

# Every XML asset must be well-formed before the governance suite runs.
while IFS= read -r dmn; do
  python3 -c "import sys,xml.dom.minidom; xml.dom.minidom.parse(sys.argv[1])" "${dmn}" \
    || { echo "FAIL: malformed XML asset ${dmn}" >&2; exit 1; }
done < <(find "${RULES_DIR}" -type f -name '*.dmn')

echo "[rules] ${ASSETS} DRL/DMN assets present; running governance suite"
if ! (cd "${REPO_ROOT}" && ./mvnw -q -pl tests/integration -Dtest='RuleGovernanceTest' test); then
  echo "FAIL: rules governance suite did not pass" >&2
  exit 1
fi

echo "PASS: rules governance suite green"
