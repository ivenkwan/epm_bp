package dev.epm.core.treasuryworkflow;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Daily treasury calendar (Task 22): entity cutoffs in UTC with exact T0 boundary semantics
 * (received at T0 is on time; T0+1s is late), original receiving timestamps preserved verbatim, and
 * prior-period submissions never refreshing today's state.
 */
@ApplicationScoped
public class TreasuryCalendarService {

    /** Lateness classification against a cutoff. */
    public enum Outcome {
        ON_TIME,
        LATE_AT_T0_PLUS,
        MISSING
    }

    /** Classified receipt: outcome plus the preserved original receiving timestamp. */
    public record Receipt(Outcome outcome, java.time.OffsetDateTime receivedAtUtc) {}

    /**
     * Classifies one receipt. {@code receivedAtUtc == cutoff} is on time (inclusive); anything
     * after is late; null means missing.
     */
    public Receipt classify(
            java.time.OffsetDateTime cutoff, java.time.OffsetDateTime receivedAtUtc) {
        if (receivedAtUtc == null) {
            return new Receipt(Outcome.MISSING, null);
        }
        if (!receivedAtUtc.isAfter(cutoff)) {
            return new Receipt(Outcome.ON_TIME, receivedAtUtc);
        }
        return new Receipt(Outcome.LATE_AT_T0_PLUS, receivedAtUtc);
    }

    /** The cutoff instant for an entity and business date (UTC wall clock). */
    public java.time.OffsetDateTime cutoffFor(
            String entityId, java.time.LocalDate businessDate, java.time.LocalTime cutoffUtc) {
        return businessDate.atTime(cutoffUtc).atOffset(java.time.ZoneOffset.UTC);
    }

    /** Stale prior-period receipt: after cutoff of its own period, it never counts. */
    public boolean countsForPeriod(
            java.time.LocalDate submissionPeriod,
            java.time.LocalDate evaluatedPeriod,
            Outcome outcome) {
        return submissionPeriod.equals(evaluatedPeriod) && outcome != Outcome.MISSING;
    }
}
