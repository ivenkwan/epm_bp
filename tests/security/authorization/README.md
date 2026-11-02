# Authorization security suite

Executable location: `services/finance-admin/src/test/java/dev/epm/admin/AuthorizationSecurityTest.java`
(QuarkusTest so the suite runs against the real service with its governed schema; it
moves to a dedicated runner module when services are deployed separately).

Covered contracts (F-039, AC-F-039-01..03):

- allowed access is audited with session/role evidence (`epm_access_decision`)
- restricted entity denied server-side with **no value leakage** in the response body
- empty scope returns zero entities (row-level scope)
- self-approval denied (`self-approval is prohibited`)
- conflicting preparer+approver roles denied with an `SOD_CONFLICT` escalation
- action outside role denied (assurance-reader cannot approve adjustments)
- overdue review denies, escalates, and removes the entity from the visible scope
  (never grants)
- gateway bypass (direct HTTP, no APISIX) faces identical denial semantics

Note: OIDC SSO/MFA enforcement at the edge is verified by `PlatformSmokeTest` (Task 4:
anonymous 401, token-authenticated 200 through APISIX).
