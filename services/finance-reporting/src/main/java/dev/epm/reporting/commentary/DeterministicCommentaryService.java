package dev.epm.reporting.commentary;

import java.math.BigDecimal;
import java.util.List;

/**
 * Task 51 deterministic commentary: narrative assembled by template from certified metrics,
 * variances, and approved narrative rules with evidence references. This generator has no AI
 * dependency whatsoever — it is the complete fallback that keeps reporting whole with {@code
 * AI_ENABLED=false}.
 */
public class DeterministicCommentaryService {

    /** One certified metric with its evidence reference. */
    public record Metric(String kpiId, String period, BigDecimal value, String evidenceRef) {}

    /** One governed variance line with its evidence reference. */
    public record Variance(
            String kpiId, BigDecimal amount, BigDecimal percentage, String evidenceRef) {}

    /** Approved narrative rule (threshold + template fragment). */
    public record NarrativeRule(
            BigDecimal adverseThreshold, String adverseFragment, String favourableFragment) {}

    /** A generated commentary block (deterministic). */
    public record Commentary(String kpiId, String text, List<String> evidenceRefs) {}

    /** Generates deterministic commentary for one KPI from certified inputs. */
    public Commentary comment(Metric metric, Variance variance, NarrativeRule rule) {
        StringBuilder text = new StringBuilder();
        text.append(metric.kpiId())
                .append(" for ")
                .append(metric.period())
                .append(" is ")
                .append(metric.value().toPlainString())
                .append('.');
        if (variance != null && variance.percentage() != null) {
            boolean adverse =
                    variance.percentage().abs().compareTo(rule.adverseThreshold().abs()) >= 0
                            && variance.amount().signum() < 0;
            boolean favourable =
                    variance.percentage().abs().compareTo(rule.adverseThreshold().abs()) >= 0
                            && variance.amount().signum() > 0;
            if (adverse) {
                text.append(' ').append(rule.adverseFragment());
            } else if (favourable) {
                text.append(' ').append(rule.favourableFragment());
            }
            text.append(" Variance ")
                    .append(variance.amount().toPlainString())
                    .append(" (")
                    .append(variance.percentage().toPlainString())
                    .append(").");
        }
        List<String> evidence = new java.util.ArrayList<>();
        evidence.add(metric.evidenceRef());
        if (variance != null) {
            evidence.add(variance.evidenceRef());
        }
        return new Commentary(metric.kpiId(), text.toString(), List.copyOf(evidence));
    }
}
