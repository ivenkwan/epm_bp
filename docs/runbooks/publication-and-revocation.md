# Publication and Revocation Runbook

## Edition lifecycle

```
DRAFT → (review: comments resolved) → PUBLISHED → SUPERSEDED (by a newer edition)
                                            ↘ REVOKED (visible withdrawal)
DRAFT → REJECTED (evidence preserved)
```

- **Nothing publishes automatically.** Publication is always a named human action
  (publisher recorded); timers never publish, close, or approve.
- **Published editions are immutable** and fully linked: object revision, narrative,
  publisher, and the evidence bundle (Task 20 certification).
- **Supersede, don't overwrite**: a corrected-source refresh creates a new edition
  number; the old edition remains exactly reproducible.
- **Revoke is visible, not deletional**: a revoked edition stays in history with the
  revocation actor and timestamp.

## Commentary gate

- Review comments must be **resolved** (ADDRESSED or REJECTED) before publication;
  open comments block with a safe reason.
- Comment bodies are bounded (2000 chars, schema-enforced); rejected comments and
  editions are preserved as history.
- **Unsupported citations are rejected**: narratives citing evidence that does not
  exist block publication.

## Refresh scope

Weekly/monthly refresh recalculates affected products from corrected sources;
propagation failures or failed controls **leave the last certified edition available**
and raise an owned exception (Task 14 case). The exact-cutoff / one-second-late
semantics of Task 22 govern which submissions enter the refresh scope.

## Stale pending tasks

On resubmission, stale pending tasks for the superseded draft are cancelled (the new
edition's tasks replace them).
