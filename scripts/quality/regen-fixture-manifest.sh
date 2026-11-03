#!/usr/bin/env bash
# Regenerate db/fixtures/provenance.manifest (SHA-256 per file + authorship header).
# Run from repository root after any fixture change; FixtureIntegrityTest verifies drift.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."

{
  echo "# EPM synthetic fixture provenance"
  echo "# synthetic_only=true"
  echo "# prepared_by=R0 engineering session 2026-11-02"
  echo "# independent_finance_review=PENDING (tracked in docs/operations/r0-build-status.md)"
  echo "# algorithm=sha256"
  while read -r f; do
    printf '%s  %s\n' "$(sha256sum "$f" | cut -d' ' -f1)" "$f"
  done < <(find db/fixtures -type f ! -name provenance.manifest | sort)
} > db/fixtures/provenance.manifest

echo "manifest updated with $(grep -vc '^#' db/fixtures/provenance.manifest) file digests"
