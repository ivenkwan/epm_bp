package dev.epm.planning.calendar;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Task 43 planning-calendar governance: milestones, task ownership, multi-level approvals, due
 * dates, reminders, overdue escalation, and the final lock. Approvals progress strictly level by
 * level; every required level needs an independent eligible actor (never the preparer) before the
 * final lock; rejection returns the cycle to preparation and cancels stale higher-level tasks;
 * reminders and escalations change visibility only — no timer ever approves, locks, publishes, or
 * closes. All durable state is Java/PostgreSQL-owned; decisions form an append-only chronology.
 */
public class PlanningCalendarService {

    public enum TaskStatus {
        OPEN,
        COMPLETED,
        CANCELLED,
        ESCALATED
    }

    public enum Action {
        SUBMIT,
        APPROVE,
        RETURN,
        COMMENT,
        DELEGATE,
        ESCALATE,
        REMIND,
        LOCK,
        CANCEL
    }

    /** Durable planning cycle (from {@code epm_planning_cycle}). */
    public record Cycle(
            String cycleId,
            String planId,
            String name,
            String status,
            String businessCalendar,
            int targetBusinessDays,
            String preparer,
            Instant openedAtUtc) {}

    /** One calendar task (from {@code epm_planning_task}); level 0 is preparation. */
    public record Task(
            String taskId,
            String cycleId,
            String assignee,
            String delegatedFrom,
            int level,
            String title,
            Instant dueAtUtc,
            TaskStatus status,
            Instant reminderSentUtc,
            Instant escalatedAtUtc,
            String completedBy) {

        public Task asStatus(TaskStatus next) {
            return new Task(
                    taskId,
                    cycleId,
                    assignee,
                    delegatedFrom,
                    level,
                    title,
                    dueAtUtc,
                    next,
                    reminderSentUtc,
                    escalatedAtUtc,
                    completedBy);
        }

        public Task withCompletedBy(String actor) {
            return new Task(
                    taskId,
                    cycleId,
                    assignee,
                    delegatedFrom,
                    level,
                    title,
                    dueAtUtc,
                    status,
                    reminderSentUtc,
                    escalatedAtUtc,
                    actor);
        }
    }

    /** Append-only decision/comment chronology entry. */
    public record Decision(
            String taskId, String actor, Action action, String comment, Instant atUtc) {}

    /** Full Java-owned cycle state. */
    public record CycleState(
            Cycle cycle,
            List<Task> tasks,
            List<Decision> decisions,
            int currentLevel,
            int maxLevel,
            boolean finalLocked) {}

    /** Outcome of an approval-level action. */
    public record StepOutcome(
            CycleState state, boolean accepted, String note, Optional<Task> activatedNext) {

        static StepOutcome refused(CycleState state, String note) {
            return new StepOutcome(state, false, note, Optional.empty());
        }
    }

    /** Reminder/escalation outcome (visibility only). */
    public record DueOutcome(Task task, boolean reminderSent, boolean escalated, String note) {}

    /**
     * Opens a cycle: preparation task (level 0) active, approval levels defined by the ordered
     * assignees, decisions chronology empty.
     */
    public CycleState openCycle(
            Cycle cycle, List<String> approvalAssignees, Instant nowUtc, Duration levelDueAfter) {
        List<Task> tasks = new ArrayList<>();
        tasks.add(
                new Task(
                        cycle.cycleId() + ":prep",
                        cycle.cycleId(),
                        cycle.preparer(),
                        null,
                        0,
                        "Prepare schedules and plan version",
                        nowUtc.plus(levelDueAfter),
                        TaskStatus.OPEN,
                        null,
                        null,
                        null));
        for (int i = 0; i < approvalAssignees.size(); i++) {
            tasks.add(
                    new Task(
                            cycle.cycleId() + ":lvl" + (i + 1),
                            cycle.cycleId(),
                            approvalAssignees.get(i),
                            null,
                            i + 1,
                            "Approval level " + (i + 1),
                            nowUtc.plus(levelDueAfter.multipliedBy(i + 2)),
                            TaskStatus.OPEN,
                            null,
                            null,
                            null));
        }
        return new CycleState(cycle, tasks, new ArrayList<>(), 0, approvalAssignees.size(), false);
    }

    /** Preparation completion (submit) makes level 1 actionable. */
    public StepOutcome submit(CycleState state, String actor, Instant nowUtc) {
        Task prep = taskAt(state, 0);
        if (!prep.assignee().equals(actor)) {
            return StepOutcome.refused(state, "only the preparer submits");
        }
        if (prep.status() != TaskStatus.OPEN) {
            return StepOutcome.refused(state, "preparation task is " + prep.status());
        }
        CycleState updated =
                replace(
                        state,
                        prep.asStatus(TaskStatus.COMPLETED).withCompletedBy(actor),
                        new Decision(prep.taskId(), actor, Action.SUBMIT, null, nowUtc),
                        1);
        return new StepOutcome(
                updated,
                true,
                "submitted; level 1 now actionable",
                Optional.of(taskAt(updated, 1)));
    }

    /**
     * Completes the current approval level. APPROVE at the final level performs the final lock
     * (named-human act); RETURN cancels all open tasks (stale) and reopens preparation. The actor
     * must be the task's assignee and must not be the preparer (independent approval at every
     * level).
     */
    public StepOutcome actOnLevel(
            CycleState state,
            int level,
            Action action,
            String actor,
            String comment,
            Instant nowUtc) {
        if (action != Action.APPROVE && action != Action.RETURN) {
            return StepOutcome.refused(state, "level action must be APPROVE or RETURN");
        }
        if (state.finalLocked()) {
            return StepOutcome.refused(
                    state, "cycle is locked; reopen goes through plan-version reopening");
        }
        if (level != state.currentLevel() || level < 1) {
            return StepOutcome.refused(
                    state,
                    "approvals progress level by level: current level is " + state.currentLevel());
        }
        Task task = taskAt(state, level);
        if (task.status() != TaskStatus.OPEN && task.status() != TaskStatus.ESCALATED) {
            return StepOutcome.refused(state, "task is " + task.status());
        }
        if (!task.assignee().equals(actor)) {
            return StepOutcome.refused(state, "actor is not the assignee of this level");
        }
        if (actor.equals(state.cycle().preparer())) {
            return StepOutcome.refused(
                    state, "self-approval denied: the preparer cannot approve any level");
        }
        if (action == Action.RETURN) {
            List<Task> cancelled = new ArrayList<>();
            for (Task t : state.tasks()) {
                if (t.level() >= 1
                        && (t.status() == TaskStatus.OPEN || t.status() == TaskStatus.ESCALATED)) {
                    cancelled.add(t.asStatus(TaskStatus.CANCELLED));
                } else if (t.level() == 0 && t.status() == TaskStatus.COMPLETED) {
                    cancelled.add(t.asStatus(TaskStatus.OPEN).withCompletedBy(null));
                } else {
                    cancelled.add(t);
                }
            }
            List<Decision> decisions = new ArrayList<>(state.decisions());
            decisions.add(new Decision(task.taskId(), actor, Action.RETURN, comment, nowUtc));
            return new StepOutcome(
                    new CycleState(state.cycle(), cancelled, decisions, 0, state.maxLevel(), false),
                    true,
                    "returned to preparation; stale higher-level tasks cancelled",
                    Optional.empty());
        }
        Task completed = task.asStatus(TaskStatus.COMPLETED).withCompletedBy(actor);
        List<Decision> decisions = new ArrayList<>(state.decisions());
        decisions.add(new Decision(task.taskId(), actor, Action.APPROVE, comment, nowUtc));
        if (level == state.maxLevel()) {
            decisions.add(
                    new Decision(
                            task.taskId(), actor, Action.LOCK, "final level approved", nowUtc));
            CycleState locked =
                    new CycleState(
                            state.cycle(),
                            replaceTaskRaw(state.tasks(), completed),
                            decisions,
                            level,
                            state.maxLevel(),
                            true);
            return new StepOutcome(
                    locked, true, "final approval performed the lock", Optional.empty());
        }
        CycleState advanced =
                new CycleState(
                        state.cycle(),
                        replaceTaskRaw(state.tasks(), completed),
                        decisions,
                        level + 1,
                        state.maxLevel(),
                        false);
        return new StepOutcome(
                advanced,
                true,
                "level " + level + " approved; level " + (level + 1) + " now actionable",
                Optional.of(taskAt(advanced, level + 1)));
    }

    /**
     * Delegates an open task to another eligible actor; the chronology records who delegated to
     * whom. Independence still applies at completion time.
     */
    public StepOutcome delegate(
            CycleState state, int level, String from, String to, Instant nowUtc) {
        Task task = taskAt(state, level);
        if (task.status() != TaskStatus.OPEN && task.status() != TaskStatus.ESCALATED) {
            return StepOutcome.refused(state, "task is " + task.status());
        }
        if (!task.assignee().equals(from)) {
            return StepOutcome.refused(state, "only the current assignee can delegate");
        }
        Task delegated =
                new Task(
                        task.taskId(),
                        task.cycleId(),
                        to,
                        from,
                        task.level(),
                        task.title(),
                        task.dueAtUtc(),
                        task.status(),
                        task.reminderSentUtc(),
                        task.escalatedAtUtc(),
                        task.completedBy());
        List<Decision> decisions = new ArrayList<>(state.decisions());
        decisions.add(new Decision(task.taskId(), from, Action.DELEGATE, "to " + to, nowUtc));
        CycleState updated =
                new CycleState(
                        state.cycle(),
                        replaceTaskRaw(state.tasks(), delegated),
                        decisions,
                        state.currentLevel(),
                        state.maxLevel(),
                        state.finalLocked());
        return new StepOutcome(updated, true, "delegated to " + to, Optional.empty());
    }

    /**
     * Due-time handling: before the due time a reminder is recorded once; at/after the due time the
     * task escalates. Reminders and escalation only change visibility — the task stays open and no
     * approval, lock, publication, or closure occurs.
     */
    public DueOutcome checkDue(Task task, Instant nowUtc, Duration reminderLead) {
        if (task.status() == TaskStatus.COMPLETED || task.status() == TaskStatus.CANCELLED) {
            return new DueOutcome(task, false, false, "task already " + task.status());
        }
        if (!nowUtc.isBefore(task.dueAtUtc())) {
            if (task.escalatedAtUtc() == null) {
                Task escalated =
                        new Task(
                                task.taskId(),
                                task.cycleId(),
                                task.assignee(),
                                task.delegatedFrom(),
                                task.level(),
                                task.title(),
                                task.dueAtUtc(),
                                TaskStatus.ESCALATED,
                                task.reminderSentUtc(),
                                nowUtc,
                                task.completedBy());
                return new DueOutcome(
                        escalated,
                        false,
                        true,
                        "overdue: escalated for visibility — never auto-approved");
            }
            return new DueOutcome(task, false, true, "already escalated");
        }
        if (task.reminderSentUtc() == null
                && !nowUtc.isBefore(task.dueAtUtc().minus(reminderLead))) {
            Task reminded =
                    new Task(
                            task.taskId(),
                            task.cycleId(),
                            task.assignee(),
                            task.delegatedFrom(),
                            task.level(),
                            task.title(),
                            task.dueAtUtc(),
                            task.status(),
                            nowUtc,
                            task.escalatedAtUtc(),
                            task.completedBy());
            return new DueOutcome(reminded, true, false, "reminder recorded — visibility only");
        }
        return new DueOutcome(task, false, false, "within lead time; nothing due");
    }

    /** Business days (weekdays) between two instants — measurement against the target. */
    public static long businessDaysBetween(Instant from, Instant to) {
        ZonedDateTime cursor = from.atZone(ZoneOffset.UTC);
        ZonedDateTime end = to.atZone(ZoneOffset.UTC);
        long days = 0;
        while (cursor.toLocalDate().isBefore(end.toLocalDate())) {
            cursor = cursor.plusDays(1);
            int dow = cursor.getDayOfWeek().getValue();
            if (dow >= 1 && dow <= 5) {
                days++;
            }
        }
        return days;
    }

    /** Cycle-time measurement against the stabilized five-business-day target (report only). */
    public record CycleMeasurement(
            long elapsedBusinessDays, int targetBusinessDays, boolean withinTarget) {}

    public CycleMeasurement measure(CycleState state, Instant closedAtUtc) {
        long elapsed = businessDaysBetween(state.cycle().openedAtUtc(), closedAtUtc);
        return new CycleMeasurement(
                elapsed,
                state.cycle().targetBusinessDays(),
                elapsed <= state.cycle().targetBusinessDays());
    }

    // ---------- helpers ----------

    private static Task taskAt(CycleState state, int level) {
        return state.tasks().stream().filter(t -> t.level() == level).findFirst().orElseThrow();
    }

    private static List<Task> replaceTaskRaw(List<Task> tasks, Task updated) {
        List<Task> next = new ArrayList<>();
        for (Task t : tasks) {
            next.add(t.taskId().equals(updated.taskId()) ? updated : t);
        }
        return next;
    }

    private static CycleState replace(
            CycleState state, Task updated, Decision decision, int currentLevel) {
        List<Decision> decisions = new ArrayList<>(state.decisions());
        decisions.add(decision);
        return new CycleState(
                state.cycle(),
                replaceTaskRaw(state.tasks(), updated),
                decisions,
                currentLevel,
                state.maxLevel(),
                state.finalLocked());
    }
}
