package dev.epm.admin.cases;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.Set;

/**
 * Exception-case state machine (PRD 6.8.4). PostgreSQL owns the state; Kogito coordinates next
 * action, actor, timers, and escalation. Timers are advisory only. Case closure is a case decision
 * — business-object certification is separate.
 */
@ApplicationScoped
public class CaseService {

    /** Legal transitions of the common case state machine. */
    static final Map<String, Set<String>> TRANSITIONS =
            Map.ofEntries(
                    Map.entry("OPEN", Set.of("TRIAGED", "CANCELLED")),
                    Map.entry("TRIAGED", Set.of("ASSIGNED", "CANCELLED")),
                    Map.entry("ASSIGNED", Set.of("IN_PROGRESS", "CANCELLED")),
                    Map.entry(
                            "IN_PROGRESS",
                            Set.of(
                                    "WAITING_SOURCE",
                                    "WAITING_EVIDENCE",
                                    "WAITING_APPROVAL",
                                    "RESOLUTION_PROPOSED")),
                    Map.entry("WAITING_SOURCE", Set.of("IN_PROGRESS")),
                    Map.entry("WAITING_EVIDENCE", Set.of("IN_PROGRESS")),
                    Map.entry("WAITING_APPROVAL", Set.of("IN_PROGRESS")),
                    Map.entry("RESOLUTION_PROPOSED", Set.of("RESOLUTION_APPROVED", "IN_PROGRESS")),
                    Map.entry("RESOLUTION_APPROVED", Set.of("REPROCESSING")),
                    Map.entry("REPROCESSING", Set.of("VERIFIED")),
                    Map.entry("VERIFIED", Set.of("CLOSED", "IN_PROGRESS")),
                    Map.entry("CLOSED", Set.of("OPEN")),
                    Map.entry("CANCELLED", Set.of()));

    /** Applies a state transition with all human-control guards. */
    @Transactional
    public CaseEntity transition(String caseId, String targetState, String actor, String note) {
        CaseEntity entity = CaseEntity.findById(caseId);
        if (entity == null) {
            throw new CaseException("case not found: " + caseId);
        }
        String current = entity.getStatus();
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(targetState)) {
            throw new CaseException("illegal transition " + current + " -> " + targetState);
        }

        switch (targetState) {
            case "TRIAGED" -> requireOwnerAndSeverity(entity, actor);
            case "RESOLUTION_PROPOSED" -> requireEvidence(entity, actor);
            case "RESOLUTION_APPROVED" -> requireIndependentApprover(entity, actor);
            case "VERIFIED" -> requireIndependentVerifier(entity, actor);
            case "CLOSED" -> requireIndependentClosure(entity, actor);
            default -> {
                // No additional guard for coordination-only states.
            }
        }

        entity.setStatus(targetState);
        entity.touch();
        CaseTransitionEntity.record(caseId, current, targetState, actor, note);
        return entity;
    }

    /** Schedules an advisory timer (50%/75%/breach milestones). */
    @Transactional
    public CaseTimerEntity scheduleTimer(
            String caseId, String milestone, java.time.LocalDateTime dueAtUtc, String action) {
        if (!Set.of("REMIND", "REASSIGN", "ESCALATE", "VISIBILITY").contains(action)) {
            throw new CaseException("timers may only remind/reassign/escalate/change visibility");
        }
        return CaseTimerEntity.schedule(caseId, milestone, dueAtUtc, action);
    }

    /** Timer fire: advisory only; never approves, closes, certifies, or publishes. */
    @Transactional
    public void fireTimer(Long timerId) {
        CaseTimerEntity timer = CaseTimerEntity.findById(timerId);
        if (timer == null) {
            throw new CaseException("timer not found");
        }
        timer.fire(); // records the fire; no case-state effect by construction
    }

    /** Reopens a closed case with a recorded reason. */
    @Transactional
    public CaseEntity reopen(String caseId, String actor, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new CaseException("reopen requires a reason");
        }
        return transition(caseId, "OPEN", actor, "reopen: " + reason);
    }

    private static void requireOwnerAndSeverity(CaseEntity entity, String actor) {
        if (entity.getAssignedResolver() == null) {
            throw new CaseException("triage requires an assigned resolver");
        }
    }

    private static void requireEvidence(CaseEntity entity, String actor) {
        if (entity.getEvidence() == null || "[]".equals(entity.getEvidence())) {
            throw new CaseException("resolution proposal requires evidence");
        }
        entity.setAssignedResolver(
                entity.getAssignedResolver() == null ? actor : entity.getAssignedResolver());
    }

    private static void requireIndependentApprover(CaseEntity entity, String actor) {
        String resolver = entity.getAssignedResolver();
        if (resolver != null && resolver.equals(actor)) {
            throw new CaseException("resolver cannot approve their own resolution");
        }
        entity.setRequiredApprover(actor);
    }

    private static void requireIndependentVerifier(CaseEntity entity, String actor) {
        String approver = entity.getRequiredApprover();
        if (approver != null && approver.equals(actor)) {
            throw new CaseException("approver cannot verify their own approval");
        }
        entity.setIndependentVerifier(actor);
    }

    private static void requireIndependentClosure(CaseEntity entity, String actor) {
        String verifier = entity.getIndependentVerifier();
        if (verifier != null && verifier.equals(actor)) {
            throw new CaseException("verifier cannot close; closure is independent");
        }
    }

    /** Case-state governance failures. */
    public static final class CaseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public CaseException(String message) {
            super(message);
        }
    }
}
