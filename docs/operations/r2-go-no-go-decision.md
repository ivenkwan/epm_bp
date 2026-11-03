# R2 Go/No-Go Decision

Status: **PENDING — owners' decision; engineering evidence complete.**

Cutover to production treasury operation is authorized only by the named signatures
below. This record is the decision document; `docs/operations/r2-evidence-pack.md`
holds the evidence.

## Engineering readiness (all green, all reproducible)

- 186 automated tests, 0 failures, across 7 modules; 13/13 e2e.
- All quality + operations gates PASS (traceability, rules, api-types, provenance,
  migration, R1 recovery, cutover verify).
- Wave onboarding machinery with fail-closed rejection; capacity/failure gates
  delivered before scaling.
- Recovery proven into a clean AGE container with no duplicate effects; cutover phases
  rehearsed including rollback.
- Optional F-022 scope deferred fail-closed with a signed-deferral record.

## Owner inputs required (blocking cutover)

| # | Input | Owner |
|---|---|---|
| 1 | Confirmed-population workload: 2× peak + return-to-normal + parallel-run evidence (R2-M1) | Business + Technical |
| 2 | Golden/control-design sign-off (O-B1) | Finance/Treasury |
| 3 | Manual accessibility verification (O-B7) | Accessibility owner |
| 4 | Admin browser wiring acceptance or waiver (O-B8) | Product |
| 5 | OQ-004/OQ-005 parameter decisions | Named decision owners |
| 6 | Operational handover: training, severity exercise, support access, witnessed production restore (RPO/RTO measured) | Operations |

## Decision

- [ ] **GO** — cutover authorized (record date + evidence-bundle reference)
- [ ] **NO-GO** — blockers listed with owners and remediation dates
- [ ] **DEFER** — explicit re-scoping decision recorded

Signatures (name / role / date):

1. Business: ____________
2. Treasury: ____________
3. Migration: ____________
4. Control: ____________
5. Security: ____________
6. Technical: ____________
7. Operations: ____________

After an approved GO: monitor reconciliation and adoption, keep the fallback for the
authorized interval, and record **separate** human decisions for any legacy retirement
(`legacy-retirement-register.md`). Technical go-live is not retirement approval.
