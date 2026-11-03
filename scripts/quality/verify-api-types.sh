#!/usr/bin/env bash
# Verify the committed generated API types match the OpenAPI source (Task 6/7 drift gate).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WEB_DIR="${REPO_ROOT}/apps/finance-web"
GENERATED="${WEB_DIR}/src/api/generated/common-v1.ts"

command -v pnpm >/dev/null 2>&1 || { echo "FAIL: pnpm not available" >&2; exit 1; }

TEMP="$(mktemp /tmp/api-types.XXXXXX.ts)"
trap 'rm -f "${TEMP}"' EXIT

(cd "${WEB_DIR}" && pnpm --silent exec openapi-typescript \
  ../../services/finance-core/src/main/openapi/common-v1.yaml --output "${TEMP}")

# Compare bodies after the auto-generated header (first three lines may carry versions).
if ! diff <(tail -n +4 "${GENERATED}") <(tail -n +4 "${TEMP}") >/dev/null; then
  echo "FAIL: src/api/generated/common-v1.ts is stale; rerun 'pnpm --dir apps/finance-web run generate:api' and commit" >&2
  exit 1
fi

echo "PASS: generated API types match the OpenAPI source"
