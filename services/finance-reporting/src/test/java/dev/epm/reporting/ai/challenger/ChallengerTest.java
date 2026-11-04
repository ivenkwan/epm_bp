package dev.epm.reporting.ai.challenger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 54 challenger evaluation (route DISABLED in this release): ranked alerts with rationale,
 * confidence, and comparison evidence; insufficient history yields a governed no-alternative
 * outcome; human accept/dismiss/annotate/escalate decisions are retained; accepting creates a
 * separate governed scenario version — the baseline forecast and certifications are never modified
 * automatically.
 */
class ChallengerTest {

    private final ChallengerService service = new ChallengerService();

    private static List<ChallengerService.Observation> history(String... values) {
        var list = new java.util.ArrayList<ChallengerService.Observation>();
        for (int i = 0; i < values.length; i++) {
            list.add(new ChallengerService.Observation("P" + i, new BigDecimal(values[i])));
        }
        return list;
    }

    @Test
    void anomalyIsRankedWithRationaleConfidenceAndEvidence() {
        ChallengerService.Detection detection =
                service.detect(
                        history("1000.00", "1010.00", "990.00", "1005.00", "995.00"),
                        new ChallengerService.BaselineForecast("P5", new BigDecimal("800.00")),
                        new BigDecimal("3"),
                        4);
        assertThat(detection.sufficientEvidence()).isTrue();
        ChallengerService.Recommendation recommendation = detection.recommendations().get(0);
        assertThat(recommendation.rank()).isEqualTo(1);
        assertThat(recommendation.rationale()).contains("deviates").contains("MAD");
        assertThat(recommendation.confidence()).isPositive();
        assertThat(recommendation.comparisonEvidence())
                .contains("history=5")
                .contains("baseline=P5=800.00");
        assertThat(detection.fallbackNote()).contains("deterministic baseline forecast");
    }

    @Test
    void normalMovementProducesNoRecommendation() {
        ChallengerService.Detection detection =
                service.detect(
                        history("1000.00", "1010.00", "990.00", "1005.00"),
                        new ChallengerService.BaselineForecast("P4", new BigDecimal("1002.00")),
                        new BigDecimal("3"),
                        4);
        assertThat(detection.recommendations()).isEmpty();
        assertThat(detection.note()).contains("no anomaly");
    }

    @Test
    void insufficientHistoryPresentsNoUnreliableAlternative() {
        ChallengerService.Detection detection =
                service.detect(
                        history("1000.00", "1010.00"),
                        new ChallengerService.BaselineForecast("P2", new BigDecimal("800.00")),
                        new BigDecimal("3"),
                        4);
        assertThat(detection.sufficientEvidence()).isFalse();
        assertThat(detection.recommendations()).isEmpty();
        assertThat(detection.note()).contains("INSUFFICIENT_EVIDENCE");
        assertThat(detection.fallbackNote()).contains("deterministic baseline forecast");
    }

    @Test
    void humanControlsRetainDecisionsForMonitoring() {
        ChallengerService.Recommendation proposed =
                new ChallengerService.Recommendation(
                        1,
                        "rationale",
                        new BigDecimal("0.80"),
                        new BigDecimal("200.00"),
                        "evidence",
                        ChallengerService.Recommendation.Status.PROPOSED);
        ChallengerService.Recommendation accepted =
                service.act(proposed, ChallengerService.Action.ACCEPT, "analyst@epm");
        assertThat(accepted.status()).isEqualTo(ChallengerService.Recommendation.Status.ACCEPTED);
        assertThat(accepted.rationale()).contains("[ACCEPT by analyst@epm]");
        assertThat(service.act(proposed, ChallengerService.Action.DISMISS, "a@epm").status())
                .isEqualTo(ChallengerService.Recommendation.Status.DISMISSED);
        assertThat(service.act(proposed, ChallengerService.Action.ESCALATE, "a@epm").status())
                .isEqualTo(ChallengerService.Recommendation.Status.ESCALATED);
        assertThat(service.monitoringLog(List.of(accepted)))
                .anyMatch(l -> l.contains("status=ACCEPTED"));
    }

    @Test
    void acceptanceCreatesASeparateScenarioVersionNeverABaselineEdit() {
        ChallengerService.Recommendation accepted =
                new ChallengerService.Recommendation(
                        1,
                        "r",
                        BigDecimal.ONE,
                        BigDecimal.TEN,
                        "e",
                        ChallengerService.Recommendation.Status.ACCEPTED);
        assertThat(service.acceptedOutcome(accepted, "sc-challenge-1"))
                .contains("new governed scenario version sc-challenge-1")
                .contains("baseline forecast unchanged");
        ChallengerService.Recommendation dismissed =
                new ChallengerService.Recommendation(
                        1,
                        "r",
                        BigDecimal.ONE,
                        BigDecimal.TEN,
                        "e",
                        ChallengerService.Recommendation.Status.DISMISSED);
        assertThat(service.acceptedOutcome(dismissed, "x")).contains("no scenario created");
    }
}
