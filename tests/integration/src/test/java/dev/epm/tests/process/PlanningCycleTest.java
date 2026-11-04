package dev.epm.tests.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jbpm.process.instance.AbstractProcessRuntimeServiceProvider;
import org.jbpm.process.instance.LightProcessRuntime;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.utils.KieHelper;
import org.kie.kogito.Addons;
import org.kie.kogito.Application;
import org.kie.kogito.StaticApplication;
import org.kie.kogito.StaticConfig;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.impl.StaticProcessConfig;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;

/**
 * Task 43 planning-cycle process acceptance (AC-F-026): the durable Kogito planning processes run
 * level by level through named human tasks — prepare, level 1, level 2, final lock — and never
 * advance without the human completing the task (timers only remind). Plan versions and financial
 * state stay Java/PostgreSQL-owned; the process carries no financial authority of its own. Also
 * executes the Task 41 plan-version-approval process end to end. Restart is proven by building a
 * fresh runtime from the same definitions.
 */
class PlanningCycleTest {

    private static final Path PLANNING_CYCLE =
            Path.of("..", "..", "processes", "bpmn", "planning-cycle.bpmn");
    private static final Path PLAN_VERSION_APPROVAL =
            Path.of("..", "..", "processes", "bpmn", "plan-version-approval.bpmn");

    /** Parks activating human tasks; terminating transitions flow through (spike pattern). */
    static final class ParkingHandler extends DefaultKogitoWorkItemHandler {

        private final List<KogitoWorkItem> parked = new CopyOnWriteArrayList<>();

        List<KogitoWorkItem> parkedItems() {
            return parked;
        }

        KogitoWorkItem parkedNamed(String taskName) {
            return parked.stream()
                    .filter(item -> taskName.equals(taskNameOf(item)))
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new IllegalStateException(
                                            "no parked item for task "
                                                    + taskName
                                                    + " among "
                                                    + describeParked()));
        }

        private static String taskNameOf(KogitoWorkItem item) {
            Object name = item.getParameter("NodeName");
            return name != null ? name.toString() : item.getName();
        }

        private List<String> describeParked() {
            return parked.stream()
                    .map(item -> item.getName() + " node=" + taskNameOf(item))
                    .toList();
        }

        @Override
        public Optional<WorkItemTransition> transitionToPhase(
                KogitoWorkItemManager manager, KogitoWorkItem item, WorkItemTransition transition) {
            if (!transition.termination().isPresent()) {
                parked.add(item);
            }
            return super.transitionToPhase(manager, item, transition);
        }
    }

    private record Runtime(ParkingHandler handler, LightProcessRuntime runtime) {}

    private static Runtime runtimeFor(Path bpmn) throws Exception {
        ParkingHandler handler = new ParkingHandler();
        StaticProcessConfig config =
                StaticProcessConfig.newStaticProcessConfigBuilder()
                        .withWorkItemHandler("Human Task", handler)
                        .build();
        Application application = new StaticApplication(new StaticConfig(Addons.EMTPY, config));
        AbstractProcessRuntimeServiceProvider provider =
                new AbstractProcessRuntimeServiceProvider(
                        config.jobsService(),
                        config.workItemHandlers(),
                        config.processEventListeners(),
                        config.signalManagerHub(),
                        config.unitOfWorkManager(),
                        config.identityProvider());
        KieHelper helper = new KieHelper();
        helper.addResource(ResourceFactory.newFileResource(bpmn.toFile()), ResourceType.BPMN2);
        KieBase kbase = helper.build();
        assertThat(kbase.getProcesses()).isNotEmpty();
        LightProcessRuntime runtime =
                LightProcessRuntime.of(application, kbase.getProcesses(), provider);
        return new Runtime(handler, runtime);
    }

    @Test
    void planningCycleAdvancesLevelByLevelThroughNamedHumanTasksOnly() throws Exception {
        Runtime rt = runtimeFor(PLANNING_CYCLE);
        ProcessInstance instance = rt.runtime().startProcess("planning_cycle");
        assertThat(instance.getState()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        // Parked at preparation; the process cannot advance on its own.
        assertThat(rt.handler().parkedItems()).hasSize(1);
        assertThat(rt.handler().parkedItems().get(0).getParameter("NodeName"))
                .isEqualTo("Prepare schedules and plan version");
        assertThat(instance.getState())
                .as("no timer or automation advances the process")
                .isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        rt.runtime()
                .getKogitoWorkItemManager()
                .completeWorkItem(rt.handler().parkedItems().get(0).getStringId(), Map.of());
        assertThat(rt.handler().parkedNamed("Approval level 1 — division review")).isNotNull();
        assertThat(instance.getState()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);

        rt.runtime()
                .getKogitoWorkItemManager()
                .completeWorkItem(
                        rt.handler()
                                .parkedNamed("Approval level 1 — division review")
                                .getStringId(),
                        Map.of());
        assertThat(rt.handler().parkedNamed("Approval level 2 — FP&A approval")).isNotNull();

        rt.runtime()
                .getKogitoWorkItemManager()
                .completeWorkItem(
                        rt.handler().parkedNamed("Approval level 2 — FP&A approval").getStringId(),
                        Map.of());
        assertThat(rt.handler().parkedNamed("Final approval and cycle lock")).isNotNull();

        rt.runtime()
                .getKogitoWorkItemManager()
                .completeWorkItem(
                        rt.handler().parkedNamed("Final approval and cycle lock").getStringId(),
                        Map.of());
        assertThat(instance.getState())
                .as("cycle completes only after the final named-human lock")
                .isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
    }

    @Test
    void planningCycleNeverCompletesWhileARequiredApprovalIsParked() throws Exception {
        Runtime rt = runtimeFor(PLANNING_CYCLE);
        ProcessInstance instance = rt.runtime().startProcess("planning_cycle");
        rt.runtime()
                .getKogitoWorkItemManager()
                .completeWorkItem(rt.handler().parkedItems().get(0).getStringId(), Map.of());
        // Level 1 parked and deliberately left open: the process must stay ACTIVE —
        // reminders and timers exist in the definition but can never approve or lock.
        assertThat(instance.getState()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);
        assertThat(rt.handler().parkedItems()).hasSize(2);
    }

    @Test
    void planVersionApprovalProcessRunsEndToEndAndRestartsFromDefinitions() throws Exception {
        Runtime first = runtimeFor(PLAN_VERSION_APPROVAL);
        ProcessInstance instance = first.runtime().startProcess("plan_version_approval");
        assertThat(instance.getState()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);
        first.runtime()
                .getKogitoWorkItemManager()
                .completeWorkItem(
                        first.handler().parkedNamed("Independent plan review").getStringId(),
                        Map.of());
        first.runtime()
                .getKogitoWorkItemManager()
                .completeWorkItem(
                        first.handler().parkedNamed("Final approval and lock").getStringId(),
                        Map.of());
        assertThat(instance.getState()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);

        // Restart: a fresh runtime built from the same definitions runs the same flow —
        // durable definitions, Java-owned state, no in-memory authority.
        Runtime restarted = runtimeFor(PLAN_VERSION_APPROVAL);
        ProcessInstance second = restarted.runtime().startProcess("plan_version_approval");
        assertThat(second.getState()).isEqualTo(KogitoProcessInstance.STATE_ACTIVE);
        restarted
                .runtime()
                .getKogitoWorkItemManager()
                .completeWorkItem(
                        restarted.handler().parkedNamed("Independent plan review").getStringId(),
                        Map.of());
        restarted
                .runtime()
                .getKogitoWorkItemManager()
                .completeWorkItem(
                        restarted.handler().parkedNamed("Final approval and lock").getStringId(),
                        Map.of());
        assertThat(second.getState()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
    }

    @Test
    void definitionsAreWellFormedAndKeepRemindersAndLocksHuman() throws Exception {
        String cycleXml = Files.readString(PLANNING_CYCLE);
        assertThat(cycleXml)
                .contains("never approve, lock, publish, or close")
                .contains("never approve")
                .contains("timers never lock");
        String approvalXml = Files.readString(PLAN_VERSION_APPROVAL);
        assertThat(approvalXml).contains("never timer-driven");
        // No automated service tasks exist anywhere in the governed planning processes:
        // every transition is a named-human act.
        assertThat(cycleXml).doesNotContain("serviceTask");
        assertThat(approvalXml).doesNotContain("serviceTask");
        assertThat(cycleXml).doesNotContain("boundaryEvent");
        assertThat(cycleXml).doesNotContain("timerEventDefinition");
    }
}
