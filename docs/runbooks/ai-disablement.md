# Runbook: AI Disablement (Task 51)

How AI routes are disabled, killed, and kept disabled — and why reporting never
depends on them.

## Policy

1. **Ship disabled.** `config/environments/ai-routes.yaml` declares every route with
   `approved: false` under a global `ai.enabled: false` master switch. The registry
   gate (`verify-security.sh --suite ai`) fails if any route ships enabled.
2. **Enablement is a signed act.** A route is enabled only after its pilot evaluation
   passes 100% in the release set and AI/Security/Domain owners sign (Task 52–54
   gates). Enablement changes are governed changes with provenance.
3. **Deterministic fallback is complete.** Commentary, dashboards, drill, variance,
   and reporting never require a generative route. `DeterministicCommentaryService`
   carries no AI dependency at all.

## Kill switch (operator)

1. Any operator may disable: set `ai.enabled: false` (or the route's state row in
   `epm_ai_route_state` with `disabled_by`/`reason`). `AiControlPlane.disable(...)`
   records who and why.
2. Target: routes disabled **within 5 minutes** of the decision — the drill
   (`AiSecurityTest.killSwitchDisablesImmediatelyAndRecordsReason`) proves the
   mechanism is immediate; the timed operational drill is recorded per release.
3. Effect: every invocation refuses safely ("AI disabled by operator kill switch —
   deterministic path active"); in-flight drafts lose publication eligibility.

## Failure handling

- Invalid numeric claims, injection text, uncertified snapshots, missing provenance:
  output suppressed (`SUPPRESSED`), never presented as fact.
- Suspected leak or bad output: disable the route first, investigate second; the
   provenance trail (`epm_ai_provenance`) holds route/model/prompt/snapshot/output
   references with redacted telemetry.

## Verification

```
./scripts/quality/verify-security.sh --suite ai
```

Runs the control-plane negative suites (grounding + security), asserts the registry
ships fully disabled, and scans it for credential literals.
