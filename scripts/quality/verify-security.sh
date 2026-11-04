#!/usr/bin/env bash
# Security gate. Default: dependency/secret scans (R0). `--suite ai` (Task 51+):
# AI control-plane negative tests plus registry checks — routes ship disabled with no
# credential literals.
set -euo pipefail
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root_dir=$(cd "$script_dir/../.." && pwd)
scan_root=${SECURITY_SCAN_ROOT:-$root_dir}
cd "${scan_root}"

if [ "${1:-}" = "--suite" ] && [ "${2:-}" = "ai" ]; then
  # 1. AI control-plane + deterministic commentary tests (grounding and security).
  if ! ./mvnw -q -pl services/finance-reporting -Dtest='AiGroundingTest,AiSecurityTest' test; then
    printf 'ERROR: AI grounding/security negative tests failed\n' >&2
    exit 1
  fi
  # 2. Every route in the registry ships disabled and unapproved.
  routes_yaml="config/environments/ai-routes.yaml"
  [ -f "${routes_yaml}" ] || { printf 'ERROR: AI route registry missing\n' >&2; exit 1; }
  if grep -n "enabled: true" "${routes_yaml}"; then
    printf 'ERROR: AI route registry must ship with every route disabled\n' >&2
    exit 1
  fi
  if ! grep -q "ai:" "${routes_yaml}" || ! grep -q "approved: false" "${routes_yaml}"; then
    printf 'ERROR: AI route registry must declare the ai block with unapproved routes\n' >&2
    exit 1
  fi
  # 3. No credential literals: model references must be env-referenced placeholders.
  if grep -nE "(api[_-]?key|password|secret|token)[\"' ]*[:=][\"' ]*[A-Za-z0-9/+_-]{8,}" \
      "${routes_yaml}"; then
    printf 'ERROR: credential-like literal found in the AI route registry\n' >&2
    exit 1
  fi
  printf 'PASS: AI security suite (control-plane negatives, disabled registry, no credential literals)\n'
  exit 0
fi

for command_name in trivy gitleaks; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'ERROR: required security scanner is unavailable: %s; install the versions in docs/decisions/version-matrix.md\n' "$command_name" >&2
    exit 1
  fi
done

trivy fs \
  --scanners vuln,secret \
  --severity HIGH,CRITICAL \
  --include-dev-deps \
  --exit-code 1 \
  --skip-dirs .git,.worktrees,.superpowers,node_modules,target,dist,build,coverage,tests/ai-evaluation \
  .
gitleaks detect --source . --no-git --redact
printf 'PASS: high/critical dependency and secret scans found no findings in current toolchain inputs\n'
