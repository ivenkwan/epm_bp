package dev.epm.planning.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task 43 acceptance (AC-F-026-01–03): level-by-level progression with independent actors,
 * self-approval denied, rejection returns to preparation and cancels stale tasks, resubmission
 * works, due-time reminders and breach escalation change visibility only (never
 * approve/lock/publish/close), delegation is recorded, decisions survive restart, and cycle time is
 * measured against the five-business-day target without claiming it.
 */
class PlanningCalendarTest {

    private static final Instant T0 = Instant.parse("2026-11-02T09:00:00Z"); // a Monday
    private static final Duration DUE = Duration.ofHours(48);

    private final PlanningCalendarService service = new PlanningCalendarService();

    private PlanningCalendarService.CycleState twoLevelCycle() {
        return service.openCycle(
                new PlanningCalendarService.Cycle(
                        "cyc-1",
                        "plan-bud-2027",
                        "FY2027 budget cycle",
                        "OPEN",
                        "weekdays",
                        5,
                        "planner@epm",
                        T0),
                List.of("division@epm", "fpa@epm"),
                T0,
                DUE);
    }

    @Test
    void approvalsProgressLevelByLevelUntilTheFinalLock() {
        PlanningCalendarService.CycleState state = twoLevelCycle();
        // Level 1 is not actionable before submission.
        PlanningCalendarService.StepOutcome premature =
                service.actOnLevel(
                        state, 1, PlanningCalendarService.Action.APPROVE, "division@epm", null, T0);
        assertThat(premature.accepted()).isFalse();
        assertThat(premature.note()).contains("level by level");

        PlanningCalendarService.CycleState submitted =
                service.submit(state, "planner@epm", T0).state();
        PlanningCalendarService.CycleState levelOne =
                service.actOnLevel(
                                submitted,
                                1,
                                PlanningCalendarService.Action.APPROVE,
                                "division@epm",
                                "ok",
                                T0)
                        .state();
        assertThat(levelOne.currentLevel()).isEqualTo(2);

        PlanningCalendarService.StepOutcome locked =
                service.actOnLevel(
                        levelOne,
                        2,
                        PlanningCalendarService.Action.APPROVE,
                        "fpa@epm",
                        "final",
                        T0);
        assertThat(locked.accepted()).isTrue();
        assertThat(locked.state().finalLocked()).isTrue();
        assertThat(locked.note()).contains("final approval performed the lock");
        // LOCK decision recorded in the chronology.
        assertThat(locked.state().decisions())
                .anyMatch(d -> d.action() == PlanningCalendarService.Action.LOCK);
    }

    @Test
    void selfApprovalAndWrongAssigneeAreDenied() {
        PlanningCalendarService.CycleState submitted =
                service.submit(twoLevelCycle(), "planner@epm", T0).state();
        PlanningCalendarService.StepOutcome self =
                service.actOnLevel(
                        submitted,
                        1,
                        PlanningCalendarService.Action.APPROVE,
                        "planner@epm",
                        null,
                        T0);
        // planner@epm is not the assignee AND is the preparer — both refusals apply.
        assertThat(self.accepted()).isFalse();

        // Preparer as explicit assignee would still be denied by independence.
        PlanningCalendarService.CycleState cycleWithPreparerAtLevel1 =
                service.openCycle(
                        new PlanningCalendarService.Cycle(
                                "cyc-2",
                                "plan-bud-2027",
                                "Self-approval probe",
                                "OPEN",
                                "weekdays",
                                5,
                                "planner@epm",
                                T0),
                        List.of("planner@epm", "fpa@epm"),
                        T0,
                        DUE);
        PlanningCalendarService.CycleState probe =
                service.submit(cycleWithPreparerAtLevel1, "planner@epm", T0).state();
        PlanningCalendarService.StepOutcome denied =
                service.actOnLevel(
                        probe, 1, PlanningCalendarService.Action.APPROVE, "planner@epm", null, T0);
        assertThat(denied.accepted()).isFalse();
        assertThat(denied.note()).contains("self-approval denied");

        PlanningCalendarService.StepOutcome outsider =
                service.actOnLevel(
                        submitted,
                        1,
                        PlanningCalendarService.Action.APPROVE,
                        "random@epm",
                        null,
                        T0);
        assertThat(outsider.accepted()).isFalse();
        assertThat(outsider.note()).contains("not the assignee");
    }

    @Test
    void rejectionReturnsToPreparationAndCancelsStaleHigherLevelTasks() {
        PlanningCalendarService.CycleState submitted =
                service.submit(twoLevelCycle(), "planner@epm", T0).state();
        PlanningCalendarService.CycleState levelOne =
                service.actOnLevel(
                                submitted,
                                1,
                                PlanningCalendarService.Action.APPROVE,
                                "division@epm",
                                null,
                                T0)
                        .state();
        PlanningCalendarService.StepOutcome returned =
                service.actOnLevel(
                        levelOne,
                        2,
                        PlanningCalendarService.Action.RETURN,
                        "fpa@epm",
                        "assumption rejected",
                        T0);
        assertThat(returned.accepted()).isTrue();
        PlanningCalendarService.CycleState state = returned.state();
        assertThat(state.currentLevel()).isZero();
        assertThat(state.finalLocked()).isFalse();
        assertThat(
                        state.tasks().stream()
                                .filter(t -> t.level() == 2)
                                .findFirst()
                                .orElseThrow()
                                .status())
                .isEqualTo(PlanningCalendarService.TaskStatus.CANCELLED);
        assertThat(
                        state.tasks().stream()
                                .filter(t -> t.level() == 0)
                                .findFirst()
                                .orElseThrow()
                                .status())
                .isEqualTo(PlanningCalendarService.TaskStatus.OPEN);
        // Chronology kept the rejection with comment.
        assertThat(state.decisions())
                .anyMatch(
                        d ->
                                d.action() == PlanningCalendarService.Action.RETURN
                                        && "assumption rejected".equals(d.comment()));

        // Resubmission works after rework.
        PlanningCalendarService.CycleState resubmitted =
                service.submit(state, "planner@epm", T0).state();
        assertThat(resubmitted.currentLevel()).isEqualTo(1);
    }

    @Test
    void remindersAndBreachEscalationChangeVisibilityOnly() {
        PlanningCalendarService.CycleState submitted =
                service.submit(twoLevelCycle(), "planner@epm", T0).state();
        PlanningCalendarService.Task levelOne =
                submitted.tasks().stream().filter(t -> t.level() == 1).findFirst().orElseThrow();

        // Level-1 due is T0 + 2×DUE = T0+96h; lead window opens at T0+72h.
        // Before the lead window: nothing due.
        PlanningCalendarService.DueOutcome idle =
                service.checkDue(levelOne, T0.plus(Duration.ofHours(1)), Duration.ofHours(24));
        assertThat(idle.reminderSent()).isFalse();
        assertThat(idle.escalated()).isFalse();

        // Inside the lead window: one reminder, task still OPEN.
        PlanningCalendarService.DueOutcome reminded =
                service.checkDue(levelOne, T0.plus(Duration.ofHours(80)), Duration.ofHours(24));
        assertThat(reminded.reminderSent()).isTrue();
        assertThat(reminded.task().status()).isEqualTo(PlanningCalendarService.TaskStatus.OPEN);
        assertThat(reminded.task().reminderSentUtc()).isNotNull();
        assertThat(
                        service.checkDue(
                                        reminded.task(),
                                        T0.plus(Duration.ofHours(81)),
                                        Duration.ofHours(24))
                                .reminderSent())
                .as("reminder recorded once")
                .isFalse();

        // Past due: escalation, still not completed and never approved.
        PlanningCalendarService.DueOutcome escalated =
                service.checkDue(
                        reminded.task(), T0.plus(Duration.ofHours(120)), Duration.ofHours(24));
        assertThat(escalated.escalated()).isTrue();
        assertThat(escalated.task().status())
                .isEqualTo(PlanningCalendarService.TaskStatus.ESCALATED);
        assertThat(escalated.note()).contains("never auto-approved");
        // An escalated task can still be approved by its assignee (human act).
        PlanningCalendarService.StepOutcome approvedAfterEscalation =
                service.actOnLevel(
                        submitted,
                        1,
                        PlanningCalendarService.Action.APPROVE,
                        "division@epm",
                        "approved after escalation",
                        T0.plus(Duration.ofHours(120)));
        assertThat(approvedAfterEscalation.accepted()).isTrue();
    }

    @Test
    void delegationIsRecordedAndIndependenceStillApplies() {
        PlanningCalendarService.CycleState submitted =
                service.submit(twoLevelCycle(), "planner@epm", T0).state();
        PlanningCalendarService.StepOutcome delegated =
                service.delegate(submitted, 1, "division@epm", "delegate@epm", T0);
        assertThat(delegated.accepted()).isTrue();
        PlanningCalendarService.Task delegatedTask =
                delegated.state().tasks().stream()
                        .filter(t -> t.level() == 1)
                        .findFirst()
                        .orElseThrow();
        assertThat(delegatedTask.assignee()).isEqualTo("delegate@epm");
        assertThat(delegatedTask.delegatedFrom()).isEqualTo("division@epm");
        assertThat(delegated.state().decisions())
                .anyMatch(
                        d ->
                                d.action() == PlanningCalendarService.Action.DELEGATE
                                        && "to delegate@epm".equals(d.comment()));

        // Non-assignee cannot delegate.
        assertThat(service.delegate(submitted, 1, "random@epm", "other@epm", T0).accepted())
                .isFalse();
    }

    @Test
    void decisionsSurviveRestartAndLockTerminalStateRefusesActions() {
        PlanningCalendarService.CycleState submitted =
                service.submit(twoLevelCycle(), "planner@epm", T0).state();
        PlanningCalendarService.CycleState levelOne =
                service.actOnLevel(
                                submitted,
                                1,
                                PlanningCalendarService.Action.APPROVE,
                                "division@epm",
                                null,
                                T0)
                        .state();
        // Fresh service instance continues from durable records alone.
        PlanningCalendarService fresh = new PlanningCalendarService();
        PlanningCalendarService.StepOutcome locked =
                fresh.actOnLevel(
                        levelOne, 2, PlanningCalendarService.Action.APPROVE, "fpa@epm", null, T0);
        assertThat(locked.state().finalLocked()).isTrue();
        assertThat(
                        fresh.actOnLevel(
                                        locked.state(),
                                        2,
                                        PlanningCalendarService.Action.RETURN,
                                        "fpa@epm",
                                        null,
                                        T0)
                                .accepted())
                .as("locked cycle refuses further actions")
                .isFalse();
    }

    @Test
    void cycleTimeIsMeasuredAgainstTheFiveBusinessDayTargetWithoutClaimingIt() {
        PlanningCalendarService.CycleState state = twoLevelCycle();
        // T0 = Monday 2026-11-02; Friday same week = 4 business days later.
        Instant closedFriday = Instant.parse("2026-11-06T18:00:00Z");
        PlanningCalendarService.CycleMeasurement within = service.measure(state, closedFriday);
        assertThat(within.elapsedBusinessDays()).isEqualTo(4);
        assertThat(within.withinTarget()).isTrue();

        Instant closedNextWednesday = Instant.parse("2026-11-11T18:00:00Z");
        PlanningCalendarService.CycleMeasurement exceeded =
                service.measure(state, closedNextWednesday);
        assertThat(exceeded.elapsedBusinessDays()).isEqualTo(7);
        assertThat(exceeded.withinTarget()).isFalse();
        // The target is a proposed, signed-policy measurement — not a claim of attainment.
        assertThat(exceeded.targetBusinessDays()).isEqualTo(5);
    }
}
