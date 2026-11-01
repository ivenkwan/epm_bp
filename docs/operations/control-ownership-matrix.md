# Control Ownership Matrix

| Document control | Value |
|---|---|
| Status | `PENDING_ASSIGNMENT` / `PENDING_SIGNOFF` |
| Authority baseline | PRD §6.8 and implementation roadmap Task 1 |
| Applies to | R0 foundation and subsequent release-control ownership |

## Ownership assignments

PRD role titles establish accountability, but the repository supplies no personal names. Every named-owner field therefore remains `PENDING_ASSIGNMENT`. A role title is not evidence that a named human has accepted accountability.

Escalation routes used below:

- **E1 — Delivery:** assigned owner → domain owner → Product/business owner → release acceptance owner.
- **E2 — Control:** assigned owner → Finance/Control or Security owner → independent Assurance owner → authorized executive decision-maker.
- **E3 — Incident:** detector/owner → authorized incident commander → Technical/Operations and business/control owners → independent recovery verifier. Only named humans authorize containment, recovery, or republication.

| Control domain | Accountable role | Named owner | Accountability and required independence | Escalation | Primary evidence |
|---|---|---|---|---|---|
| Product scope and releases | Business Product Owner | `PENDING_ASSIGNMENT` | Owns authoritative populations, release scope, and explicit scope exceptions; cannot unilaterally waive Must controls | E1 | Scope register, release decisions, exception register |
| Finance control | Finance Control Owner | `PENDING_ASSIGNMENT` | Owns reconciliation, adjustment, SoD, and certification policy; independent of preparers | E2 | Control design, certification evidence, adjustment ledger |
| Treasury | Treasury Owner | `PENDING_ASSIGNMENT` | Owns DTR scope, formulas, cutoffs, funding, and operating acceptance; certification remains independent | E1/E2 | Signed treasury scope, formula cards, daily evidence |
| FP&A / planning | FP&A Owner | `PENDING_ASSIGNMENT` | Owns planning rules, calendars, models, and acceptance; model preparer cannot finally approve own work | E1/E2 | Planning rules, versions, approvals, statement reconciliation |
| Data and sources | Data/Source Owners | `PENDING_ASSIGNMENT` | Own source contracts, grains, quality, precedence, control totals, and template/feed decisions | E1 | Source contracts, landing/reconciliation evidence |
| Security and privacy | Security/Privacy Owner | `PENDING_ASSIGNMENT` | Owns identity, RBAC/RLS/SoD, residency, no-training, privileged access, and security exceptions | E2/E3 | Policy exports, access reviews, security/privacy tests |
| Operations and recovery | Operations Owner | `PENDING_ASSIGNMENT` | Owns deployment, telemetry, backup/restore, fallback, and handover; recovery requires independent verification | E3 | Runbooks, SLO/cost evidence, restore and handover records |
| Model, rules, and AI | Model/AI Owner | `PENDING_ASSIGNMENT` | Owns model/rule/prompt evaluation and provenance; Domain and Security owners independently approve enablement | E2/E3 | Versioned evaluations, promotion records, kill-switch evidence |
| Migration and cutover | Migration Owner | `PENDING_ASSIGNMENT` | Owns migration register, control totals, rehearsal, archive/fallback; business/control owners decide go/no-go and retirement | E1/E2 | Migration reconciliation, parallel run, cutover decisions |
| Accessibility and UX | Accessibility Owner | `PENDING_ASSIGNMENT` | Owns WCAG 2.2 AA review, keyboard/focus/zoom/contrast/screen-reader and responsive-journey evidence | E1 | Automated/manual accessibility reports and exceptions |
| Technical delivery | Technical Owner | `PENDING_ASSIGNMENT` | Owns architecture, API compatibility, reproducible build/deployment, and remediation; no self-deployment authority | E1/E3 | Build provenance, contracts, SBOM, deployment evidence |
| Business acceptance | Acceptance Owner | `PENDING_ASSIGNMENT` | Coordinates business UAT and formal acceptance; cannot replace required domain/control sign-offs | E1 | UAT results, acceptance records, signed exceptions |
| Independent assurance | Assurance Owner | `PENDING_ASSIGNMENT` | Independently reviews traceability, control evidence, residual risk, and sign-off completeness | E2 | Assurance review, sampled evidence, release-gate sign-off |

## Segregation of duties and authority

| Controlled object/action | Prepare / propose | Approve / certify / publish | Independent verification | Prohibited authority |
|---|---|---|---|---|
| Scope and release population | Product or domain analyst | Business Product Owner with domain owners | Assurance samples population and exceptions | Unregistered “complete” claims |
| Master data, mappings, source contracts | Steward or source preparer | Independent steward/source approver | Control owner compares old/new versions and effective dates | Direct approved-version edits |
| KPI, covenant, model, rule, process, or prompt versions | Domain/model administrator | Independent domain approver; Security also approves AI route where required | Independent expected results, boundary tests, and impact review | Arbitrary user code; self-promotion; autonomous deployment |
| Submission and resubmission | Authorized entity submitter | Required entity/division approver | Reconciliation and reviewer sign-off | Overwriting original payload or revision |
| Reconciliation and certification | Preparer resolves and explains differences | Independent authorized certifier | Control owner samples source, calculation, revision, and approval evidence | Case closure as certification; self-certification |
| Manual adjustment | Authorized preparer | Independent approver; certifier remains separate where required | Independent verifier checks adjusted bridge and reversal links | Self-approval; destructive overwrite; timer/AI application |
| Commentary and report edition | Author, with optional grounded AI draft | Independent publisher | Reviewer checks evidence, citations, narrative, and edition | AI/raw-draft publication; automatic publication |
| Exception case | Resolver proposes with required evidence | Required resolution approver and independent closure authority | Independent verifier precedes closure; failed verification reopens | Resolver self-closure; closure changing business-object state |
| Delegation and access grants | Owner requests scoped, time-bound delegation | Independent authorized access approver | Periodic recertification and conflict review | Non-transferable-scope bypass; expired delegation |
| Break-glass | Named requester | Two authorized approvers with individual MFA/PAM | Independent control review within the approved target; access revoked first | Standing privilege; direct financial edit outside F-035 |
| Migration, cutover, and legacy retirement | Migration team proposes rehearsed scope | Business, Migration, Control, Security, Technical, and Operations owners as applicable | Independent totals, rollback, archive, and production verification | Automatic cutover or retirement; acceptance of unreconciled differences |

### Non-negotiable control rules

- Preparer, approver, certifier, and verifier assignments must resolve to eligible named humans and enforce independence at the time of action, not only in this document.
- Empty entitlements deny access; restricted values must not leak through rows, counts, errors, exports, prompts, evidence links, logs, or traces.
- Timers may remind, reassign, escalate, or change visibility only. They never approve, close, certify, publish, post, pay, or apply adjustments.
- Case closure and business-object certification are separate human decisions.
- AI is limited to deterministic behavior, drafts, and recommendations from certified, access-filtered snapshots. It has no payment, GL posting, approval, certification, publication, deployment, access-change, or direct financial-data authority.
- Every release exception requires an owner, rationale, remediation date, and explicit residual-risk acceptance; an exception cannot conceal a failed Must control.

## Acceptance and sign-off

No signature below is supplied by the repository. Each sign-off requires a named person, decision date, immutable evidence reference, and explicit acceptance statement. `PENDING_SIGNOFF` must not be interpreted as approval.

| Required sign-off | Accountable role | Named person | Decision / evidence | Status |
|---|---|---|---|---|
| Product | Business Product Owner | `PENDING_ASSIGNMENT` | `PENDING_DECISION` | `PENDING_SIGNOFF` |
| Finance | Finance Control Owner | `PENDING_ASSIGNMENT` | `PENDING_DECISION` | `PENDING_SIGNOFF` |
| Treasury | Treasury Owner | `PENDING_ASSIGNMENT` | `PENDING_DECISION` | `PENDING_SIGNOFF` |
| FP&A | FP&A Owner | `PENDING_ASSIGNMENT` | `PENDING_DECISION` | `PENDING_SIGNOFF` |
| Security | Security/Privacy Owner | `PENDING_ASSIGNMENT` | `PENDING_DECISION` | `PENDING_SIGNOFF` |
| Operations | Operations Owner | `PENDING_ASSIGNMENT` | `PENDING_DECISION` | `PENDING_SIGNOFF` |
| Assurance | Assurance Owner | `PENDING_ASSIGNMENT` | `PENDING_DECISION` | `PENDING_SIGNOFF` |
