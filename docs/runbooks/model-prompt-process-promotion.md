# Runbook: Model, Prompt, and Process Promotion (Task 55)

Governed promotion of drivers, allocations, eliminations, processes, prompts, and
models. Java remains the only authoritative commit path — **no AI, rule, process, or
prompt change can deploy itself or mutate financial facts directly.**

## Preconditions (all mandatory, fail-closed)

1. **Isolated simulation passes** against independently authored expected results
   (`RuleAdministrationService.simulate`).
2. **AGE impact is fresh** — the impact list's lineage watermark is within the
   declared staleness bound (`ImpactService.validate`); stale impact blocks promotion.
3. **Living documentation delta exists** — a governed behavior change without a
   corresponding doc/runbook delta quarantines.
4. **No suspected secrets or unapproved generated content** in the change
   (`ImpactService.quarantineScan`).
5. **Independent approval** — the approver is never the requester.
6. **Complete provenance** — ticket, test evidence, artifact/SBOM reference.

## Steps

1. Author the change (definition, old/new, expected impact).
2. Run the isolated simulation; attach the independent expected results.
3. Rebuild/check the AGE impact list; confirm freshness.
4. Attach the documentation delta and runbook update.
5. Request promotion; the independent approver reviews the full chain and approves.
6. Promote — the previous active version is preserved as `rollback-to:<version>`; a
   rejected or failed promotion leaves active versions unchanged.

## Rollback

Rollback is a promotion of the preserved previous definition — the same gates apply.
Active versions are never edited in place.

## Verification

`tests/integration/rules/AdvancedPromotionTest` exercises every refusal path and the
happy path with rollback-reference preservation.
