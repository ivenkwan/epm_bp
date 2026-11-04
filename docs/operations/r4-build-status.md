# R4 Build Status

Per-task results for the R4 advanced-planning/reporting/selective-AI release
(Tasks 47–57). Human decisions (AI pilot enablement, UX acceptance, final
acceptance) live in the release evidence and evaluation records.

| Task | Scope | Result | Evidence |
|---|---|---|---|
| 47 | Full pack/dashboard/self-service + responsive | PASS (signed p95 workloads owner-gated) | `SelfAnalysisServiceTest` 5/5; `DashboardDrillLoadTest` (20k drill bounded + exact); e2e `full-self-service.spec.ts` 4/4; restricted-entity denial, below-grain/stale-edition refusals, reconciliation incl. entitlement-change breaks |
| 48 | Drivers + isolated what-if scenarios | PASS (FP&A acceptance open) | `V033`; `ScenarioIsolationTest` 8/8 — copy isolation, range/cycle/depth refusals, replay-identical traces, promotion as new version; `drivers.dmn`; `ScenariosPanel` |
| 49 | Multi-step/step-down allocations | PASS (signed totals open) | `V034`; `AllocationIntegrityTest` 6/6 — exact step totals, largest-remainder residuals, step-down exclusion, idempotent replay, linked compensating reversal; `allocation-reversal.md` |
| 50 | Management-only eliminations | PASS (FP&A/Assurance acceptance open) | `V035`; `ManagementEliminationTest` 4/4 (integration) — exactly-once, mismatch/one-sided cases, bridge residuals, statutory denial; `management-eliminations.dmn`; MANAGEMENT_ONLY schema label |
| 51 | Deterministic commentary + AI control plane | PASS (AI routes disabled by design) | `V036`; AiGroundingTest + AiSecurityTest 9/9; `verify-security.sh --suite ai` PASS; ai-routes.yaml ships disabled, env-only credentials; kill switch + provenance + numeric fidelity |
| 52 | AI commentary pilot (Tier 1) | PASS — route DISABLED (enablement owner-gated) | `CommentaryPilotTest` 4/4 over the versioned evaluation set; drafts unpublished + provenance; independent review; `ai-commentary-review.bpmn`; `ai-commentary-evaluation.md` |
| 53 | Bounded KPI Q&A (Tier 2) | PASS — route DISABLED (enablement owner-gated) | `KpiQaTest` 6/6 — basis+citation answers, dual access filtering, governed UNAVAILABLE, injection refusal; allowlisted `SemanticTools`; `ai-qa-evaluation.md` |
| 54 | Anomaly/forecast challenger (Tier 2) | PASS — route DISABLED (enablement owner-gated) | `ChallengerTest` 5/5 — ranked rationale/confidence/evidence, INSUFFICIENT_EVIDENCE no-alternative, human controls, acceptance→separate scenario; `ai-challenger-evaluation.md` |
| 55 | Advanced rule/process administration + living provenance | PASS | `AdvancedPromotionTest` 5/5 — fail-closed promotion (simulation/impact/approval/provenance/quarantine); rollback refs; `verify-provenance.sh` living-docs check PASS; `model-prompt-process-promotion.md` |
| 57 | Mapping studio (F-045) | PASS (steward walkthrough open) | `V037`; `MappingStudioTest` 6/6 + e2e `mapping-studio.spec.ts` 4/4 — overlap/duplicate/group/intitlement refusals, rollback-as-new-version, historical resolution, impact preview |
| 56 | Final acceptance gate | PASS with owner gates open | `verify-final-recovery.sh` PASS (R1 recovery, cutover, planning migration, AI security, adversarial, workloads); `FinalAdversarialTest` 3/3 (F-043/F-044 denials); `FinalAcceptanceWorkloadTest` 2/2 (5k-line model balanced, 20k drill reconciled); `verify-release.sh --release R4` PASS; evidence pack + final acceptance + adoption report delivered |

## Open items

| ID | Item | Owner |
|---|---|---|
| R4-GATE | Final product acceptance; operational handover | All named owners |
| AI-ENABLE | Tier 1/2 route enablement gates (AI/Security/Domain sign-offs + timed drills) | AI council |
| R4-PERF | Signed 2×-peak/population performance runs (structural workloads delivered) | Technical + Business |
| R4-UX | UX matrix manual passes (screen reader, device lab, export fidelity, cross-browser) | Product + Accessibility |
| OQ-007/OQ-008/OQ-010/OQ-011/OQ-012 | Recorded open questions at gate | Decision owners |
