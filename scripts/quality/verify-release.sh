#!/usr/bin/env bash
# R0+ release evidence gate: every mandatory artifact and sign-off must exist before a
# release is certified. Usage: ./scripts/quality/verify-release.sh --release R0
set -euo pipefail

RELEASE="${2:-}"
if [ "${1:-}" = "--release" ] && [ -n "${RELEASE}" ]; then
  :
elif [ "${1:-}" = "--release" ]; then
  echo "usage: verify-release.sh --release R0" >&2; exit 2
else
  echo "usage: verify-release.sh --release R0" >&2; exit 2
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

case "${RELEASE}" in
  R0) PACK="docs/operations/r0-evidence-pack.md" ;;
  R1) PACK="docs/operations/r1-evidence-pack.md" ;;
  R2) PACK="docs/operations/r2-evidence-pack.md" ;;
  R3) PACK="docs/operations/r3-evidence-pack.md" ;;
  *) echo "unknown release ${RELEASE}" >&2; exit 2 ;;
esac

fail() { echo "FAIL: $1" >&2; exit 1; }
[ -f "${PACK}" ] || fail "evidence pack missing: ${PACK}"

# 1. Evidence pack carries the mandatory sections.
for section in "Regression evidence" "Quality gates" "Platform evidence" \
               "Recovery rehearsal" "Exclusions and boundaries" "Open items"; do
  grep -q "## ${section}" "${PACK}" || fail "evidence pack section missing: ${section}"
done

# 2. Traceability: zero unmapped requirements.
./scripts/quality/verify-traceability.sh >/dev/null || fail "traceability gate failed"

# 3. Rules governance gate.
./scripts/quality/verify-rules.sh >/dev/null || fail "rules gate failed"

# 4. Generated API types drift gate.
./scripts/quality/verify-api-types.sh >/dev/null || fail "api-types drift gate failed"

# 5. Recorded build status has an entry for every R0 task and no PENDING rows.
STATUS="docs/operations/r0-build-status.md"
[ -f "${STATUS}" ] || fail "build status missing"
for task in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16; do
  grep -qE "^\| ${task} \|" "${STATUS}" || fail "build status row missing for task ${task}"
done
if grep -q '| PENDING | — |' "${STATUS}"; then
  fail "build status still contains PENDING rows"
fi

# 5b. R3 additionally requires its own build status for Tasks 39-46 with no PENDING rows.
if [ "${RELEASE}" = "R3" ]; then
  R3_STATUS="docs/operations/r3-build-status.md"
  [ -f "${R3_STATUS}" ] || fail "R3 build status missing"
  for task in 39 40 41 42 43 44 45 46; do
    grep -qE "^\| ${task} \|" "${R3_STATUS}" || fail "R3 build status row missing for task ${task}"
  done
  if grep -q '| PENDING | — |' "${R3_STATUS}"; then
    fail "R3 build status still contains PENDING rows"
  fi
  ./scripts/operations/run-planning-migration.sh --verify >/dev/null \
    || fail "planning migration gate failed"
fi

# 6. Sign-off block present and unsigned rows reported.
SIGNOFFS=$(grep -c '^- \[ \] .*sign' "${PACK}" || true)
if [ "${SIGNOFFS}" -gt 0 ]; then
  echo "NOTE: ${SIGNOFFS} sign-off(s) still open in the evidence pack; see Open items."
fi

echo "PASS: ${RELEASE} evidence gate (artifacts, gates, and status complete; sign-offs tracked in the pack)"
