# AI Evaluation Suites

Executable evaluation suites for the governed AI routes (Tasks 51–54). The suites
live with their owning deployable so they run in the same toolchain as the code they
certify; this directory is the evaluation entry point referenced by the traceability
register and release evidence.

| Evaluation | Route | Executable suite | Status |
|---|---|---|---|
| `grounding/` | control plane | `services/finance-reporting/src/test/java/dev/epm/reporting/ai/AiGroundingTest.java` | green (route disabled) |
| `security/` | control plane | `services/finance-reporting/src/test/java/dev/epm/reporting/ai/AiSecurityTest.java` | green (route disabled) |
| `commentary/` | commentary-draft (Task 52) | `services/finance-reporting/src/test/java/dev/epm/reporting/ai/commentary/CommentaryPilotTest.java` | green (route disabled) |
| `qa/` | kpi-qa (Task 53) | `services/finance-reporting/src/test/java/dev/epm/reporting/ai/qa/KpiQaTest.java` | green (route disabled) |
| `challenger/` | forecast-challenger (Task 54) | `services/finance-reporting/src/test/java/dev/epm/reporting/ai/challenger/ChallengerTest.java` | green (route disabled) |

Gate: `./scripts/quality/verify-security.sh --suite ai` runs the control-plane suites
and asserts the registry ships fully disabled with no credential literals. A route is
enabled only after its pilot evaluation passes 100% in the release set and
AI/Security/Domain owners sign; until then every evaluation here certifies the
**disabled** behavior plus the deterministic fallback.
