# AI Authority Boundary (Task 51)

The absolute boundary every AI capability obeys. It is enforced structurally (the
control plane refuses everything by default), not by policy alone.

## AI may never

1. **Approve, publish, certify, or post** — no AI output reaches a published edition,
   certification, plan version, or ledger without a named human approver.
2. **Modify financial facts** — plans, actuals, allocations, eliminations: immutable
   ledger entries only; AI cannot write them.
3. **Hold credentials** — endpoints/keys are environment-referenced only
   (`EPM_AI_*_MODEL_REF`); nothing usable exists in code, examples, or tests.
4. **See beyond entitlements** — certified snapshots are server-side
   RBAC/RLS/object-filtered **before** context creation; access filtering repeats on
   evidence-link retrieval.
5. **Deploy itself** — prompt/model/route changes are governed promotions (Task 55)
   requiring signed approval; no autonomous deployment path exists.
6. **Present unverified numbers** — numeric claims are extracted and compared exactly
   (value and scale) to cited decimal strings; mismatches are suppressed.

## AI may (only when its route is signed-enabled)

- Draft commentary text (Tier 1) that a human edits/rejects/publishes.
- Answer bounded KPI questions (Tier 2) from allowlisted semantic tools with
  citations or a safe refusal.
- Recommend anomalies/challenger forecasts (Tier 2) that humans accept/dismiss.

## Enforcement points

| Control | Where |
|---|---|
| Route admission (approval + certification + kill switch) | `AiControlPlane.admit` |
| Numeric fidelity (exact value + scale) | `AiControlPlane.numericFidelity` |
| Independent human review | `AiControlPlane.review` |
| Registry ships disabled | `config/environments/ai-routes.yaml` + `verify-security.sh --suite ai` |
| Provenance of every attempt | `epm_ai_provenance` (incl. REFUSED) |
| Operator kill switch | `epm_ai_route_state` / `ai.enabled` |

The deterministic reporting path (`DeterministicCommentaryService` and every R0–R3
capability) has no AI dependency and remains fully functional with AI disabled —
verified by `AiGroundingTest.deterministicCommentaryIsCompleteWithAiDisabled`.
