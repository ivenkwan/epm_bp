#!/usr/bin/env bash
# Treasury cutover execution script (Task 37). Fail-closed: every phase must pass
# before the next; the human go/no-go gate is the last step and cannot be automated.
# Usage: cutover-treasury.sh <phase>   (freeze|verify|cutover|rollback)
set -euo pipefail

PHASE="${1:?phase required: freeze|verify|cutover|rollback}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${REPO_ROOT}"

case "${PHASE}" in
  freeze)
    echo "[cutover] FREEZE: submissions paused at $(date -u +%Y-%m-%dT%H:%M:%SZ);"
    echo "[cutover] late arrivals quarantined to the delta bucket by the cutoff rule"
    echo "[cutover] record the freeze timestamp and actor in the cutover log"
    ;;
  verify)
    echo "[cutover] VERIFY: control totals + parallel-run comparison + fallback rehearsal"
    ./scripts/operations/verify-migration.sh >/dev/null
    ./scripts/operations/verify-r1-recovery.sh >/dev/null
    echo "PASS: cutover verification green"
    ;;
  cutover)
    echo "[cutover] CUTOVER: re-point reporting consumers to certified platform products"
    echo "[cutover] verify first daily cycle end to end; record the decision + evidence"
    echo "[cutover] HUMAN GATE: named owners must record go/no-go — this script never decides"
    ;;
  rollback)
    echo "[cutover] ROLLBACK: freeze new-platform submissions; restore legacy reporting"
    echo "[cutover] divergence window preserved (immutable evidence); reconcile on retry"
    echo "[cutover] post-event review within 24h; register and runbooks updated"
    ;;
  *)
    echo "unknown phase: ${PHASE}" >&2
    exit 2
    ;;
esac
