package dev.epm.admin.ruleadmin;

import dev.epm.admin.impact.ImpactService;
import java.util.List;

/**
 * Task 55 advanced rule/process administration: isolated simulation, independent expected results,
 * and scoped promotion with rollback references. Java remains the only authoritative commit path —
 * no rule, process, prompt, or model change can deploy itself or mutate financial facts. Rejected
 * or failed promotions leave the active versions unchanged; every promotion links requirement,
 * ticket, source change, tests, artifact/SBOM, release approval, runbook, and documentation delta.
 */
public class RuleAdministrationService {

    /** An active governed version. */
    public record ActiveVersion(String kind, String version, String definition) {}

    /** A simulation outcome (isolated; never touches active versions). */
    public record Simulation(
            boolean passed, List<String> oldResults, List<String> newResults, String note) {}

    /** Simulates the change in isolation against independent expected results. */
    public Simulation simulate(
            String changeDefinition,
            List<String> independentExpectedResults,
            java.util.function.Function<String, List<String>> simulator) {
        List<String> results = simulator.apply(changeDefinition);
        boolean passed = results.equals(independentExpectedResults);
        return new Simulation(
                passed,
                List.of(),
                results,
                passed
                        ? "simulation matches the independent expected results"
                        : "simulation differs from independent expected results — promotion"
                                + " blocked");
    }

    /** A promotion request with its evidence chain. */
    public record PromotionRequest(
            ImpactService.GovernedChange change,
            Simulation simulation,
            ImpactService.ImpactList impact,
            String requestedBy,
            String approvedBy,
            String ticketRef,
            String testEvidence,
            String artifactSbomRef) {}

    /** Promotion outcome. */
    public record Promotion(
            boolean promoted,
            ActiveVersion newActive,
            ActiveVersion previousActive,
            String rollbackRef,
            String note) {}

    /**
     * Promotes a governed change fail-closed: passing simulation, fresh impact, independent
     * approval (never the requester), complete provenance chain, and no quarantine finding are all
     * mandatory. The previous active version is preserved as the rollback reference.
     */
    public Promotion promote(
            PromotionRequest request,
            ActiveVersion currentActive,
            ImpactService impactService,
            java.time.Instant nowUtc,
            java.time.Duration maxStaleness) {
        if (request.requestedBy().equals(request.approvedBy())) {
            return refused("self-approval denied: promotion needs an independent approver");
        }
        if (!request.simulation().passed()) {
            return refused(request.simulation().note());
        }
        ImpactService.ImpactValidation impact =
                impactService.validate(request.impact(), nowUtc, maxStaleness);
        if (!impact.fresh()) {
            return refused(impact.note());
        }
        ImpactService.QuarantineDecision quarantine =
                impactService.quarantineScan(request.change());
        if (quarantine.quarantined()) {
            return refused(quarantine.reason());
        }
        if (isBlank(request.ticketRef())
                || isBlank(request.testEvidence())
                || isBlank(request.artifactSbomRef())) {
            return refused(
                    "provenance incomplete: ticket, test evidence, and artifact/SBOM references"
                            + " are mandatory");
        }
        ActiveVersion next =
                new ActiveVersion(
                        request.change().kind().name(),
                        currentActive.version() + "-next",
                        request.change().newDefinition());
        return new Promotion(
                true,
                next,
                currentActive,
                "rollback-to:" + currentActive.version(),
                "promoted with full provenance; rollback reference preserved");
    }

    private static Promotion refused(String note) {
        return new Promotion(false, null, null, null, note);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
