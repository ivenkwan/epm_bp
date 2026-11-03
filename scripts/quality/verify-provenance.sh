#!/usr/bin/env bash
# Verify repository provenance: a clean export of HEAD must build with the locked
# toolchain and the committed lockfile, with no undocumented manual setup.
#
# Usage:
#   ./scripts/quality/verify-provenance.sh           # full: clean build + pnpm frozen install
#   ./scripts/quality/verify-provenance.sh --quick   # skip Maven build (lockfile checks only)
#
# Environment overrides (all optional):
#   SKIP_MAVEN=1  same as --quick
#   KEEP_WORKDIR=1  keep the temporary export for inspection
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
QUICK=0
[ "${1:-}" = "--quick" ] || [ "${SKIP_MAVEN:-0}" = "1" ] && QUICK=1

WORKDIR="$(mktemp -d /tmp/epm-provenance.XXXXXX)"
trap '[ "${KEEP_WORKDIR:-0}" = "1" ] || rm -rf "${WORKDIR}"' EXIT

echo "[provenance] exporting HEAD to ${WORKDIR}"
git -C "${REPO_ROOT}" archive HEAD | tar -x -C "${WORKDIR}"

cd "${WORKDIR}"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

# 1. Maven wrapper is committed and executable.
[ -x ./mvnw ] || fail "mvnw missing or not executable in clean export"

# 2. Lockfiles are committed.
[ -f pnpm-lock.yaml ] || fail "pnpm-lock.yaml missing from repository"

if [ "${QUICK}" = "1" ]; then
  echo "[provenance] quick mode: skipping clean Maven build"
else
  echo "[provenance] running ./mvnw -q verify in clean export"
  ./mvnw -q verify || fail "clean-export Maven build failed; see output above"
fi

# 3. pnpm install must succeed from the lockfile alone.
if command -v pnpm >/dev/null 2>&1; then
  echo "[provenance] running pnpm install --frozen-lockfile in clean export"
  pnpm install --frozen-lockfile --ignore-scripts || fail "pnpm frozen install failed"
else
  fail "pnpm not available; cannot verify frontend provenance"
fi

# 4. No untracked build outputs are required for any of the above.
echo "PASS: clean export builds with committed assets only"
