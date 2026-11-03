# G1 Scale Decision

Status: **PENDING — owners' decision; engineering evidence complete.**

Per the plan, R2 (scale) starts only on an explicit G1 decision. The engineering side
of every mandatory input is delivered and green (`docs/operations/r1-evidence-pack.md`):

- Functional acceptance: 173 automated tests, 0 failures, plus 11/11 e2e.
- Golden fidelity: every calculator reproduces the independently hand-calculated
  expectations §1–§5 (cash, headroom, covenants, intercompany, reconciliation,
  adjustments, migration).
- Security: authorization, break-glass, delegation, and export entitlement suites all
  green; telemetry redaction proven on the live platform.
- Resilience: restart/restore/AGE-rebuild drills green; migration fallback enforced
  until GO; treasury manual-fallback runbook delivered.
- Documentation: 15 runbooks + designs current.

What engineering cannot supply (human inputs, tracked in the evidence pack):

| # | Input | Owner |
|---|---|---|
| 1 | Representative-cohort parallel run + performance measurements (R1-M1) | Business + Technical owners |
| 2 | Golden/control-design sign-off (O-B1) | Finance/Treasury |
| 3 | Manual accessibility verification (O-B7) | Accessibility owner |
| 4 | OQ-004 partial-publication rules; OQ-005 forecast parameters | Named decision owners |
| 5 | Training/adoption record for representative users and operators | Operations |

Decision (check exactly one; record sign-offs below):

- [ ] **scale** — all mandatory evidence and measurements pass
- [ ] **remediate** — correctable gaps returned to named R1 tasks (list them)
- [ ] **stop** — financial integrity, security, or human authority cannot be assured

Sign-offs: Product / Finance & Treasury / Technical / Security / Operations /
Migration / Control — with date.
