# Management-Only Eliminations (Task 50)

Intercompany counterparty plans match on shared keys within a declared tolerance.
**Matched pairs eliminate exactly once** under an independently approved run;
**mismatches and one-sided lines open cases** with entity owners (playbook 7) — never
an automatic elimination or one-sided overwrite. Both sides and all differences are
preserved in the reconciled bridge.

## Non-negotiable boundary (F-043 exclusion)

Every output carries the **MANAGEMENT_ONLY** label (enforced by the schema CHECK).
These eliminations are a management view only — they must never be represented as
statutory consolidation, legal consolidation, or statutory reporting. Statutory
requests are denied by design (`EliminationService.statutoryRequest`).

## Semantics

1. **Matching** — PAY/RECEIVE pairs per key; difference = |pay − receive|; within
   tolerance ⇒ match; beyond ⇒ mismatch case; missing side ⇒ one-sided case.
2. **Elimination** — the lower of the two sides, exactly once per key per run;
   replaying the same run id is refused ("would eliminate twice"). Approval is
   independent and named.
3. **Bridge** — total eliminated + per-key residuals (both sides preserved);
   e.g. 500.00 vs 500.005 eliminates 500.00 with a 0.01 residual declared.
4. **Cases** — mismatch cases carry key, difference, owner entity, status; resolution
   is the existing governed case flow with FP&A review and independent approver.
5. **Ledger** — `epm_elimination_entry` is immutable with stable source/run IDs and
   plan-version provenance.

Governed policy also encoded in `rules/dmn/planning/management-eliminations.dmn`
(`ELIMINATE_MANAGEMENT_ONLY` / `OPEN_CASE_MISMATCH` / `OPEN_CASE_ONE_SIDED`).
