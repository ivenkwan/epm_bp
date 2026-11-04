package dev.epm.admin.impact;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Task 55 impact analysis: governed changes (driver, allocation, elimination, process, prompt,
 * model) carry old/new impact lists validated against fresh AGE lineage projections. Stale impact
 * blocks promotion; the impact of a change is computed before any promotion is attempted.
 */
public class ImpactService {

    /** One impacted downstream object (from the AGE projection). */
    public record Impacted(String objectType, String objectId, String reason) {}

    /** An impact list with its lineage watermark. */
    public record ImpactList(List<Impacted> impacted, Instant lineageWatermarkUtc) {}

    /** The governed change under analysis. */
    public record GovernedChange(
            String changeId,
            Kind kind,
            String oldDefinition,
            String newDefinition,
            String expectedImpactHash,
            String docDeltaRef) {

        public enum Kind {
            DRIVER,
            ALLOCATION,
            ELIMINATION,
            PROCESS,
            PROMPT,
            MODEL
        }
    }

    /** Validation outcome. */
    public record ImpactValidation(boolean fresh, String note, ImpactList list) {}

    /** Validates that the impact list is fresh against the declared staleness bound. */
    public ImpactValidation validate(ImpactList list, Instant nowUtc, Duration maxStaleness) {
        if (list == null || list.lineageWatermarkUtc() == null) {
            return new ImpactValidation(false, "no impact list — promotion blocked", list);
        }
        Duration age = Duration.between(list.lineageWatermarkUtc(), nowUtc);
        if (age.compareTo(maxStaleness) > 0) {
            return new ImpactValidation(
                    false,
                    "stale AGE impact ("
                            + age
                            + " old, bound "
                            + maxStaleness
                            + ") — rebuild lineage before promotion",
                    list);
        }
        return new ImpactValidation(true, "impact fresh", list);
    }

    /** Quarantine check: suspected secrets or unapproved generated content. */
    public QuarantineDecision quarantineScan(GovernedChange change) {
        String definition = change.newDefinition() == null ? "" : change.newDefinition();
        if (definition.matches("(?s).*(password|secret|api[_-]?key)\\s*[:=]\\s*\\S{8,}.*")) {
            return new QuarantineDecision(
                    true, "suspected secret in change content — quarantined for human remediation");
        }
        if (change.docDeltaRef() == null || change.docDeltaRef().isBlank()) {
            return new QuarantineDecision(
                    true,
                    "living-documentation check failed: governed behavior changes require a"
                            + " doc/runbook delta");
        }
        return new QuarantineDecision(false, null);
    }

    /** Quarantine outcome. */
    public record QuarantineDecision(boolean quarantined, String reason) {}
}
