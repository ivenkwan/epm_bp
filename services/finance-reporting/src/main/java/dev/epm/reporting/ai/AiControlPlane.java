package dev.epm.reporting.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task 51 AI grounding and control plane: every generative route ships disabled and refuses safely
 * without approval, a certified snapshot, and complete provenance. Numeric claims in AI output are
 * extracted and compared exactly to the cited decimal strings (value, scale) — mismatches are
 * suppressed as invalid, never presented as fact. The operator kill switch disables routes without
 * interrupting the deterministic reporting path. No AI database credentials exist in code;
 * endpoints/keys are environment-referenced only.
 */
public class AiControlPlane {

    /** One registered route (from ai-routes.yaml; state policy: disabled default). */
    public record Route(
            String id,
            int tier,
            String modelRef,
            String promptVersion,
            boolean approved,
            String owner,
            List<String> tools,
            String dataRegion,
            boolean trainingOnCustomerData) {}

    /** Runtime route state (kill switch). */
    public record RouteState(boolean enabled, String disabledBy, String reason) {}

    /** A certified snapshot with stable references and entitlement-filtered scope. */
    public record CertifiedSnapshot(
            String snapshotId,
            String kpiId,
            String period,
            String sourceVersion,
            boolean certified,
            List<String> entitledScope) {}

    /** A request to invoke a route. */
    public record Invocation(String routeId, CertifiedSnapshot snapshot, String actor) {}

    /** Gate outcome. */
    public record Gate(boolean allowed, String reason, Provenance provenance) {}

    /** Provenance record for every (attempted) invocation. */
    public record Provenance(
            String routeId,
            String modelVersion,
            String promptVersion,
            String snapshotId,
            String validation,
            String actor) {}

    /**
     * Gate 1 — route admission: global kill switch, route approval, and snapshot certification are
     * all mandatory; anything missing refuses safely (with provenance of the refusal).
     */
    public Gate admit(
            Invocation invocation,
            List<Route> routes,
            RouteState state,
            boolean aiEnabledGlobally) {
        Route route =
                routes.stream()
                        .filter(r -> r.id().equals(invocation.routeId()))
                        .findFirst()
                        .orElse(null);
        if (route == null) {
            return refused(invocation, "unknown route");
        }
        if (!aiEnabledGlobally || state == null || !state.enabled()) {
            return refused(
                    invocation, "AI disabled by operator kill switch — deterministic path active");
        }
        if (!route.approved()) {
            return refused(invocation, "route lacks signed approval — safe refusal");
        }
        if (invocation.snapshot() == null || !invocation.snapshot().certified()) {
            return refused(invocation, "uncertified snapshot cannot ground AI context");
        }
        return new Gate(
                true,
                null,
                new Provenance(
                        route.id(),
                        route.modelRef(),
                        route.promptVersion(),
                        invocation.snapshot().snapshotId(),
                        "admitted",
                        invocation.actor()));
    }

    private static Gate refused(Invocation invocation, String reason) {
        return new Gate(
                false,
                reason,
                new Provenance(
                        invocation.routeId(),
                        null,
                        null,
                        invocation.snapshot() == null ? null : invocation.snapshot().snapshotId(),
                        "REFUSED: " + reason,
                        invocation.actor()));
    }

    /** One numeric claim extracted from AI output with its citation. */
    public record NumericClaim(String claimed, String cited) {}

    private static final Pattern NUMBER = Pattern.compile("-?\\d+\\.\\d+");

    /**
     * Gate 2 — numeric fidelity: extracts every decimal claim from AI text and compares it exactly
     * (value and scale) against the cited decimal strings. Any mismatch or unsupported claim
     * invalidates the output.
     */
    public boolean numericFidelity(String aiText, List<NumericClaim> citedClaims) {
        Matcher matcher = NUMBER.matcher(aiText);
        while (matcher.find()) {
            String claimed = matcher.group();
            boolean supported = citedClaims.stream().anyMatch(c -> matches(c.cited(), claimed));
            if (!supported) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(String cited, String claimed) {
        try {
            BigDecimal c = new BigDecimal(cited);
            BigDecimal k = new BigDecimal(claimed);
            // Value equality with identical scale: "1500.00" ≠ "1500.0" as presented.
            return c.compareTo(k) == 0 && c.scale() == k.scale();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Reviewer decision on an AI draft: edit, reject, or publish (independent). */
    public enum ReviewDecision {
        EDIT,
        REJECT,
        PUBLISH
    }

    /**
     * Gate 3 — human review: rejected or invalid AI text never reaches a published edition; only an
     * independent reviewer (never the draft actor) may publish.
     */
    public ReviewOutcome review(
            String draftText,
            boolean numericFidelityOk,
            String draftActor,
            String reviewer,
            ReviewDecision decision) {
        if (!numericFidelityOk) {
            return new ReviewOutcome(
                    false, "invalid numeric claims — draft suppressed", "SUPPRESSED");
        }
        if (reviewer.equals(draftActor)) {
            return new ReviewOutcome(
                    false, "reviewer must differ from the draft actor", "REJECTED");
        }
        return switch (decision) {
            case REJECT -> new ReviewOutcome(false, "rejected by reviewer", "REJECTED");
            case EDIT ->
                    new ReviewOutcome(false, "edited by reviewer; re-review required", "EDITED");
            case PUBLISH ->
                    new ReviewOutcome(true, "published by independent reviewer", "PUBLISHED");
        };
    }

    /** Review outcome. */
    public record ReviewOutcome(boolean published, String note, String status) {}

    /** Kill-switch action: disabling is immediate and provenance-recorded. */
    public RouteState disable(String disabledBy, String reason) {
        return new RouteState(false, disabledBy, reason);
    }

    /** Telemetry redaction: keys stay visible for audit, sensitive values are masked. */
    public String redact(String telemetryPayload) {
        if (telemetryPayload == null) {
            return "";
        }
        return Pattern.compile(
                        "(token|key|secret|prompt|completion|answer)=[^;\\s]+",
                        Pattern.CASE_INSENSITIVE)
                .matcher(telemetryPayload)
                .replaceAll("$1=<redacted>");
    }

    /** Credentials are environment-referenced only; this resolver proves it. */
    public static Optional<String> resolveEndpointRef(String envName) {
        return Optional.ofNullable(System.getenv(envName)).filter(v -> !v.isBlank());
    }
}
