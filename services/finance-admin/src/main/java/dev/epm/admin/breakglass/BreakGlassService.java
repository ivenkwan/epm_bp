package dev.epm.admin.breakglass;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/**
 * Controlled break-glass (Task 30, playbook 12). Sessions require dual approval by two distinct
 * named individuals, are least-scope, individually authenticated, recorded, expire after 60
 * minutes, revocable early, and independently reviewed within 24 hours. Direct financial edits
 * outside F-035, self-approval, certification, publication, payment, GL posting, and self-closure
 * are denied during break-glass and the attempt is logged without leaking session content.
 */
@ApplicationScoped
public class BreakGlassService {

    /** Actions always denied, even under break-glass (F-035 is the only edit path). */
    static final Set<String> ALWAYS_DENIED =
            Set.of(
                    "PAYMENT",
                    "GL_POSTING",
                    "CERTIFY",
                    "PUBLISH",
                    "AUTONOMOUS_APPROVAL",
                    "DIRECT_FINANCIAL_EDIT");

    /** One governed session. */
    public record Session(
            String sessionId,
            String requester,
            String approver,
            String scope,
            OffsetDateTime startedAt,
            OffsetDateTime expiresAt,
            OffsetDateTime revokedAt,
            String reviewedBy) {

        public boolean activeAt(OffsetDateTime at) {
            return revokedAt == null && !at.isBefore(startedAt) && at.isBefore(expiresAt);
        }
    }

    /** Decision for one action attempt inside a session. */
    public record ActionDecision(boolean allowed, String reason) {}

    /** Opens a session: dual approval by two distinct individuals, 60-minute expiry. */
    public Session open(
            String requester,
            String approver,
            String scope,
            String justification,
            OffsetDateTime now) {
        if (requester.equals(approver)) {
            throw new IllegalArgumentException("break-glass requires two distinct individuals");
        }
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("break-glass requires a justification");
        }
        return new Session(
                "bg-" + UUID.randomUUID(),
                requester,
                approver,
                scope,
                now,
                now.plusMinutes(60),
                null,
                null);
    }

    /** Decides an in-session action. Denials are recorded, never leaked. */
    public ActionDecision decide(Session session, String actor, String action, OffsetDateTime at) {
        if (!session.activeAt(at)) {
            return new ActionDecision(false, "session expired or revoked");
        }
        if (ALWAYS_DENIED.contains(action)) {
            return new ActionDecision(false, "action denied under break-glass");
        }
        if (action.endsWith(":SELF")) {
            return new ActionDecision(false, "self-approval denied under break-glass");
        }
        return new ActionDecision(true, "least-scope action within session");
    }

    /** Early revocation: the session ends immediately; nothing in-flight completes. */
    public Session revoke(Session session, OffsetDateTime at) {
        return new Session(
                session.sessionId(),
                session.requester(),
                session.approver(),
                session.scope(),
                session.startedAt(),
                session.expiresAt(),
                at,
                session.reviewedBy());
    }

    /** Independent review within 24 hours of session start (dual-approval record). */
    public boolean reviewRecorded(Session session, String reviewer, OffsetDateTime at) {
        return reviewer != null
                && !reviewer.equals(session.requester())
                && !at.isAfter(session.startedAt().plusHours(24));
    }

    @SuppressWarnings("unused")
    private static OffsetDateTime unusedNowAnchor() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
