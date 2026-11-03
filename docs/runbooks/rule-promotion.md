# Rule Promotion Runbook

## Scope

Versioned Drools (DRL) and DMN assets under `rules/`. Rules are deterministic,
explainable, terminating, and side-effect free; database commits and workflow
transitions never live in rules.

## Promotion flow

1. **Candidate isolation** — a candidate rule/DMN version is evaluated in a fresh
   KieBase against its fixtures (`RuleGovernanceTest` pattern); it never replaces the
   active version before all expectations pass.
2. **Independent expected results** — every rule row ships with expectations authored
   independently of the implementation (Task 3 golden + adversarial fixtures + per-rule
   boundary cases in the governance suite).
3. **Approval & activation** — promotion is a governed change: approvals and versions
   are recorded with the asset change; a rejected candidate leaves the active version
   and financial facts untouched (asserted by the candidate-rejection test).
4. **Rollback** — the previous version remains available as a versioned asset;
   reactivation is a new governed change, not a destructive edit.

## Invariants (enforced by the gate)

- `scripts/quality/verify-rules.sh` runs on every CI build:
  - all XML assets well-formed;
  - `RuleGovernanceTest` green: per-rule expected quarantine outcomes, missing-input
    and boundary rows, rule-order independence, bounded termination (no re-fire over
    unchanged facts), DMN table row coverage incl. zero-denominator and null-numerator
    boundaries, and candidate-rejection leaving facts unchanged.
- Hit policies are explicit; explanations carry rule name, expected vs actual, and
  coordinates (violation code on the fact).
- Backward-chained goals stay bounded (cycle/depth limits land with their owning
  tasks' assets).

## Adding a rule set

1. Add the asset under `rules/drools/<domain>/` or `rules/dmn/<domain>/` with a
   versioned name and header comment.
2. Author expected results independently; add rows to the governance suite (or the
   owning task's suite, e.g. `ValidationBoundaryTest` in Task 19).
3. Run `./scripts/quality/verify-rules.sh` — it must pass before commit.
