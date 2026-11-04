package dev.epm.planning.schedules;

import dev.epm.planning.statements.IntegratedStatementService;
import java.time.Instant;
import java.util.List;

/**
 * Task 42 schedule and imbalance-case governance: versioned non-driver input schedules feed the
 * deterministic statement engine; imbalance blocks submission/approval and opens a case carrying
 * statement, period, entity, schedule, difference, model/rule versions, and the calculation trace.
 * Closing a case is a case-management act only — it never approves or publishes the plan.
 */
public class ScheduleService {

    /** Imbalance case record (persisted as {@code epm_imbalance_case}). */
    public record ImbalanceCase(
            String caseId,
            String planId,
            String entityId,
            String period,
            String statementType,
            String scheduleId,
            String difference,
            String modelVersion,
            String ruleVersion,
            String calcTrace,
            String status,
            Instant openedAtUtc) {}

    /** Submission outcome. */
    public record SubmitOutcome(boolean submitted, String reason, ImbalanceCase openedCase) {}

    /**
     * Submits a plan for approval only when its calculation is CALCULATED. Any other status blocks
     * and, for IMBALANCE, opens a case with full coordinates.
     */
    public SubmitOutcome submitForApproval(
            String planId,
            String entityId,
            String period,
            String scheduleId,
            IntegratedStatementService.CalculationOutcome outcome,
            String caseId,
            Instant nowUtc) {
        if (outcome.status() == IntegratedStatementService.Status.MISSING_REQUIRED_INPUT) {
            return new SubmitOutcome(
                    false,
                    "required input missing at "
                            + outcome.errorCoordinate()
                            + " — submission blocked",
                    null);
        }
        if (outcome.status() == IntegratedStatementService.Status.IMBALANCE) {
            ImbalanceCase opened =
                    new ImbalanceCase(
                            caseId,
                            planId,
                            entityId,
                            period,
                            "BS",
                            scheduleId,
                            outcome.statements().balanceDifference().toPlainString(),
                            IntegratedStatementService.MODEL_VERSION,
                            IntegratedStatementService.RULE_VERSION,
                            outcome.calcTrace(),
                            "OPEN",
                            nowUtc);
            return new SubmitOutcome(
                    false,
                    "statements imbalanced — submission blocked and case " + caseId + " opened",
                    opened);
        }
        return new SubmitOutcome(true, "balanced statements attached to submission", null);
    }

    /**
     * Closes an imbalance case. Case closure is bookkeeping only: it can never approve or publish
     * the plan — the plan must independently pass a balanced calculation and its own approval
     * chain.
     */
    public record CaseClosure(ImbalanceCase closedCase, String planStatusAfter, String note) {}

    public CaseClosure closeCase(ImbalanceCase openCase, String closer, String planStatus) {
        if (!"OPEN".equals(openCase.status()) && !"IN_PROGRESS".equals(openCase.status())) {
            throw new IllegalArgumentException("case " + openCase.caseId() + " is not open");
        }
        ImbalanceCase closed =
                new ImbalanceCase(
                        openCase.caseId(),
                        openCase.planId(),
                        openCase.entityId(),
                        openCase.period(),
                        openCase.statementType(),
                        openCase.scheduleId(),
                        openCase.difference(),
                        openCase.modelVersion(),
                        openCase.ruleVersion(),
                        openCase.calcTrace(),
                        "CLOSED",
                        openCase.openedAtUtc());
        return new CaseClosure(
                closed,
                planStatus,
                "case closed by "
                        + closer
                        + "; plan status remains '"
                        + planStatus
                        + "' — closure never approves or publishes");
    }

    /**
     * Validates schedule completeness for the governed required line keys; a null value keeps its
     * coordinate (zero is valid, null is not).
     */
    public record Validation(String scheduleId, List<String> missingOrNull) {}

    public Validation validate(
            String scheduleId, List<IntegratedStatementService.ScheduleLine> lines) {
        List<String> problems =
                lines.stream()
                        .filter(line -> line.value() == null)
                        .map(IntegratedStatementService.ScheduleLine::coordinate)
                        .toList();
        return new Validation(scheduleId, problems);
    }
}
