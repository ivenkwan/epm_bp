#!/usr/bin/env bash
set -euo pipefail
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root_dir=$(cd "$script_dir/../.." && pwd)
scan_root=${SECURITY_SCAN_ROOT:-$root_dir}
cd "$scan_root"

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
  --skip-dirs .git,.worktrees,.superpowers,node_modules,target,dist,build,coverage \
  .
gitleaks detect --source . --no-git --redact
printf 'PASS: high/critical dependency and secret scans found no findings in current toolchain inputs\n'
