package dev.epm.reporting.ai.challenger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Task 54 anomaly detection and forecast challenger (Tier 2): ranked alerts with rationale,
 * confidence, and comparison evidence over certified history; insufficient evidence yields a
 * governed INSUFFICIENT_EVIDENCE outcome — an unreliable alternative is never presented.
 * Recommendations never modify baseline forecasts or certify anything: humans
 * accept/dismiss/annotate/escalate, and an accepted recommendation becomes a separate governed
 * scenario version (Task 48), not an edit. The deterministic forecast remains the fallback.
 */
public class ChallengerService {

    /** One certified historical observation. */
    public record Observation(String period, BigDecimal value) {}

    /** The approved baseline forecast for the next period. */
    public record BaselineForecast(String period, BigDecimal value) {}

    /** A challenger recommendation. */
    public record Recommendation(
            int rank,
            String rationale,
            BigDecimal confidence,
            BigDecimal observedDeviation,
            String comparisonEvidence,
            Status status) {

        public enum Status {
            PROPOSED,
            ACCEPTED,
            DISMISSED,
            ANNOTATED,
            ESCALATED
        }
    }

    /** Detection outcome. */
    public record Detection(
            List<Recommendation> recommendations,
            boolean sufficientEvidence,
            String note,
            String fallbackNote) {}

    /**
     * Detects anomalies against certified history: mean-absolute-deviation threshold with a
     * declared minimum history. Below the minimum, no recommendation is made — the deterministic
     * forecast stands.
     */
    public Detection detect(
            List<Observation> history,
            BaselineForecast baseline,
            BigDecimal thresholdMultiplier,
            int minimumHistory) {
        if (history.size() < minimumHistory) {
            return new Detection(
                    List.of(),
                    false,
                    "INSUFFICIENT_EVIDENCE: only "
                            + history.size()
                            + " observations (minimum "
                            + minimumHistory
                            + ") — no alternative is presented",
                    "deterministic baseline forecast remains authoritative");
        }
        BigDecimal sum =
                history.stream().map(Observation::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(history.size()), 6, RoundingMode.HALF_UP);
        BigDecimal deviationSum = BigDecimal.ZERO;
        for (Observation observation : history) {
            deviationSum = deviationSum.add(observation.value().subtract(mean).abs());
        }
        BigDecimal mad =
                deviationSum.divide(BigDecimal.valueOf(history.size()), 6, RoundingMode.HALF_UP);
        BigDecimal deviation =
                baseline.value().subtract(mean).abs().setScale(6, RoundingMode.HALF_UP);
        BigDecimal bound = mad.multiply(thresholdMultiplier).setScale(6, RoundingMode.HALF_UP);
        if (deviation.compareTo(bound) <= 0 || mad.signum() == 0 && deviation.signum() == 0) {
            return new Detection(
                    List.of(),
                    true,
                    "no anomaly: baseline deviation "
                            + deviation.toPlainString()
                            + " within "
                            + bound.toPlainString(),
                    "deterministic baseline forecast remains authoritative");
        }
        BigDecimal confidence =
                bound.signum() == 0
                        ? BigDecimal.ONE
                        : deviation.divide(deviation.add(bound), 2, RoundingMode.HALF_UP);
        return new Detection(
                List.of(
                        new Recommendation(
                                1,
                                "baseline "
                                        + baseline.value().toPlainString()
                                        + " deviates "
                                        + deviation.toPlainString()
                                        + " from certified history mean "
                                        + mean.toPlainString()
                                        + " (bound "
                                        + bound.toPlainString()
                                        + " MAD × "
                                        + thresholdMultiplier.toPlainString()
                                        + ")",
                                confidence,
                                deviation,
                                "history="
                                        + history.size()
                                        + " periods; mean="
                                        + mean.toPlainString()
                                        + "; MAD="
                                        + mad.toPlainString()
                                        + "; baseline="
                                        + baseline.period()
                                        + "="
                                        + baseline.value().toPlainString(),
                                Recommendation.Status.PROPOSED)),
                true,
                "1 ranked recommendation; human review required — no automatic change",
                "deterministic baseline forecast remains authoritative");
    }

    /** Human action on a recommendation; every decision is retained for monitoring. */
    public Recommendation act(Recommendation recommendation, Action action, String actor) {
        Recommendation.Status next =
                switch (action) {
                    case ACCEPT -> Recommendation.Status.ACCEPTED;
                    case DISMISS -> Recommendation.Status.DISMISSED;
                    case ANNOTATE -> Recommendation.Status.ANNOTATED;
                    case ESCALATE -> Recommendation.Status.ESCALATED;
                };
        return new Recommendation(
                recommendation.rank(),
                recommendation.rationale() + " [" + action + " by " + actor + "]",
                recommendation.confidence(),
                recommendation.observedDeviation(),
                recommendation.comparisonEvidence(),
                next);
    }

    /** Human actions. */
    public enum Action {
        ACCEPT,
        DISMISS,
        ANNOTATE,
        ESCALATE
    }

    /**
     * Accepting a recommendation proposes a separate governed scenario version — the baseline
     * forecast itself is never modified.
     */
    public String acceptedOutcome(Recommendation accepted, String scenarioRef) {
        if (accepted.status() != Recommendation.Status.ACCEPTED) {
            return "recommendation is " + accepted.status() + " — no scenario created";
        }
        return "accepted recommendation materializes as new governed scenario version "
                + scenarioRef
                + "; baseline forecast unchanged";
    }

    /** Monitoring record of retained analyst rationale. */
    public List<String> monitoringLog(List<Recommendation> recommendations) {
        List<String> log = new ArrayList<>();
        for (Recommendation recommendation : recommendations) {
            log.add(
                    "rank="
                            + recommendation.rank()
                            + " status="
                            + recommendation.status()
                            + " rationale="
                            + recommendation.rationale());
        }
        return log;
    }
}
