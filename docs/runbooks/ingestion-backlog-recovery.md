# Ingestion Backlog Recovery Runbook

## Symptoms

- Outbox backlog growing (`epm_outbox` pending rows), ingestion runs QUEUED beyond
  schedule, or the AGE projection watermark lagging the relational watermark
  (`epm_lineage_projection.source_watermark` behind).

## Triage

1. Quantify: pending outbox count, oldest pending age, queue age of ingestion runs,
   watermark delta. Saturation alerts fire per the autoscaling policy.
2. Classify: burst (expected peak), stuck worker (job claims stalled), or repeated
   delivery failure (`dispatch_attempts` climbing with `last_error`).

## Recovery sequence

1. **Scale workers** on queue age/depth (the platform autoscales; manual scale is the
   same lever). Financial semantics are unchanged by infrastructure scaling.
2. **Drain in order**: the outbox dispatcher claims batches with SKIP LOCKED — safe to
   run more workers; redelivery is idempotent (stable event ids; consumers dedupe).
3. **Clear the ingestion backlog**: scheduled runs execute per their batch identity;
   replays return the original run and land no duplicate facts (Task 17).
4. **Catch the projection up**: outbox-driven differential application (or the full
   rebuild script for large gaps) brings the watermark current; dependent certification
   stays blocked until `isFresh` passes (Task 21).
5. **Verify**: pending count zero, watermark ≥ relational watermark, a reconciliation
   spot-check on affected outputs, then publication resumes.

## Targets

Synthetic backlog clearance within the proposed 60 minutes is a measured target on the
signed workload (R2-M1 class evidence), not an assumption; the capacity/failure gates
in `tests/performance/treasury/PeakBacklogTest.java` cover the lossless-drain and
lag-recovery semantics before any scale claim.

## Escalation

Backlog past the alert threshold with no drainage in one schedule period opens a
severity-two case with the accountable platform owner; the treasury manual-fallback
runbook applies if certification for the day is at risk.
