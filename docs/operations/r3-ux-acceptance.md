# R3 UX Acceptance Record (Task 45, OQ-010)

Mandatory languages, browsers, assistive technologies, export formats, and mobile
journeys finalized for R3 reporting and planning surfaces. Human UX acceptance is a
named-owner act recorded here — nothing below is claimed as signed until the
sign-off block is completed.

## Finalized mandatory matrix (OQ-010)

| Dimension | Mandatory scope | Automated evidence | Human acceptance |
|---|---|---|---|
| Languages | English (synthetic environment) | locale formatting via BigInt-based MoneyText grouping; no locale-dependent number math | PENDING |
| Browsers | Chromium (system 1243) | Playwright e2e 21 specs across shell/reporting/treasury | PENDING (cross-browser matrix owner-gated) |
| Assistive technologies | Keyboard + screen-reader semantics (ARIA landmarks/labels), axe WCAG 2.0/2.1 A/AA | automated axe scan (no critical/serious); keyboard journey tests; mobile keyboard/focus tests | PENDING (manual screen-reader pass, O-B7) |
| Export formats | Excel, PDF, Presentation | governed export pipeline (watermark, edition-hash equivalence, retention, re-authorization, row-limit notice, delivery log) | PENDING (visual fidelity inspection of actual outputs) |
| Mobile journeys | Submission lookup; reporting consumption; planning review | 200%-zoom reflow (shell + reporting + planning), keyboard reachability at 384px width | PENDING (device-lab pass) |

## Delivered automated coverage (this release)

- `tests/e2e/accessibility/shell.spec.ts` — landmarks, skip link, keyboard nav, 200%
  zoom reflow, axe scan.
- `tests/e2e/accessibility/reporting-mobile.spec.ts` (new) — reporting and planning
  workspaces reflow at phone width (≈200% zoom) without two-dimensional scrolling;
  planning remains keyboard-operable with visible focus.
- `tests/e2e/reporting/recurring-reporting.spec.ts` (new) — certified dashboard tiles
  with freshness and lowest-governed-grain labels; all three export formats exposed
  with retention classification; publication provenance visible; in-review editions
  never claim publication; watermark/re-authorization stated.
- `tests/e2e/reporting/management-pack.spec.ts` (Task 39) — pack governance labels.

## Essential responsive journeys at 200% zoom

- Submission lookup: R0 journey, re-verified each release.
- Reporting consumption: Reports tab, dashboard tiles, recurring editions.
- Planning review: Planning tab (setup, versions, model, calendar, variance).

## Owner sign-off (UX acceptance — required before R3 exit claim)

- [ ] Product owner — journeys and matrix accepted
- [ ] Accessibility owner — manual screen-reader/device pass (O-B7)
- [ ] Business owner — export fidelity inspection (Excel/PDF/presentation outputs)
- [ ] Named owner — signed monthly-template/dashboard/drill/pack-performance workloads
