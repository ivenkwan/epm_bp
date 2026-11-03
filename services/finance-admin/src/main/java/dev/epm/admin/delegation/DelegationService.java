package dev.epm.admin.delegation;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Scoped, time-bound delegation (Task 30). Authority is non-transferable (a delegate cannot
 * re-delegate), attributed to the original owner, and expires automatically — expiry revokes
 * delegated authority without completing any approval or changing business state.
 */
@ApplicationScoped
public class DelegationService {

    /** Delegation decision for one (delegate, action, entity) request. */
    public record Decision(boolean allowed, String reason) {}

    /** One delegation record (value object for the governed table). */
    public record Delegation(
            String delegationId,
            String delegator,
            String delegate,
            String entityScope,
            String permittedAction,
            OffsetDateTime validFrom,
            OffsetDateTime validTo,
            OffsetDateTime revokedAt) {}

    /**
     * Decides whether an actor may act under delegation at the given instant. Only the direct
     * delegate can act; scopes and actions must match exactly; expiry and revocation revoke without
     * side effects.
     */
    public Decision decide(
            java.util.List<Delegation> delegations,
            String actor,
            String action,
            String entity,
            OffsetDateTime at) {
        Optional<Delegation> match =
                delegations.stream()
                        .filter(d -> d.delegate().equals(actor))
                        .filter(d -> d.permittedAction().equals(action))
                        .filter(d -> d.entityScope().equals(entity))
                        .findFirst();
        if (match.isEmpty()) {
            return new Decision(false, "no delegation covers this actor, action, and entity");
        }
        Delegation delegation = match.get();
        if (delegation.revokedAt() != null && !delegation.revokedAt().isAfter(at)) {
            return new Decision(false, "delegation revoked");
        }
        if (at.isBefore(delegation.validFrom())) {
            return new Decision(false, "delegation not yet valid");
        }
        if (!at.isBefore(delegation.validTo())) {
            // Automatic expiry: authority ends without completing any approval.
            return new Decision(false, "delegation expired");
        }
        return new Decision(true, "delegated by " + delegation.delegator());
    }

    /** Delegations can never be re-delegated by anyone, delegate or owner. */
    public boolean isTransferable(Delegation delegation, String purportedGrantor) {
        // Non-transferable by construction: no grant path exists from a held delegation.
        return false;
    }

    /** Factory for tests/seed: a delegation window with owner attribution. */
    public Delegation grant(
            String delegator,
            String delegate,
            String entityScope,
            String permittedAction,
            OffsetDateTime from,
            OffsetDateTime to) {
        if (delegator.equals(delegate)) {
            throw new IllegalArgumentException("delegator and delegate must differ");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("delegation window must be non-empty");
        }
        return new Delegation(
                "dlg-" + UUID.randomUUID(),
                delegator,
                delegate,
                entityScope,
                permittedAction,
                from,
                to,
                null);
    }

    /** Escalation is advisory: raising visibility never grants authority. */
    public Decision escalate(Decision underlying) {
        return new Decision(
                underlying.allowed(), underlying.reason() + "; escalation raises visibility only");
    }

    @SuppressWarnings("unused")
    private static OffsetDateTime unusedNowAnchor() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
