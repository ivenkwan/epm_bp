# Drools rule assets

Versioned forward- and backward-chained rule assets. Every rule ships with
independently authored expected results (Task 3 fixtures + per-rule boundary cases);
promotion flows through the governed harness in `finance-core` (`DecisionExecutor`).

## Layout

- `validation/` — deterministic validation rules over landed rows (Task 19 consumes).
- Boundary and adversarial expectations live in `db/fixtures/adversarial/` and the
  per-task expected files; nothing here is promoted without green fixture coverage.

## Invariants (enforced by `RuleGovernanceTest` and `verify-rules.sh`)

1. Rules are terminating (bounded fire count) and side-effect free.
2. Explanations are deterministic: rule name, expected vs actual, coordinates.
3. Candidate versions run in isolation; a rejected candidate leaves the active version
   and financial facts untouched.
