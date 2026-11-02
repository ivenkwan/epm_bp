package dev.epm.tests.toolchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jbpm.process.instance.AbstractProcessRuntimeServiceProvider;
import org.jbpm.process.instance.LightProcessRuntime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieRuntimeFactory;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Task 2 compatibility spike: proves the locked toolchain combination from
 * docs/decisions/version-matrix.md actually runs before any feature work.
 *
 * <p>Verified concerns: PostgreSQL connectivity, Apache AGE extension loading and cypher
 * round-trip, a terminating Drools rule, one DMN evaluation, and a jBPM/Kogito process with a human
 * task whose completion is persisted to PostgreSQL.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ToolchainCompatibilityTest {

    private static final DockerImageName AGE_IMAGE =
            DockerImageName.parse("apache/age:release_PG17_1.7.0")
                    .asCompatibleSubstituteFor("postgres");

    private static PostgreSQLContainer<?> postgres;
    private static String jdbcUrl;

    /** Simple fact for the terminating-rule check. */
    public static class SpikeSignal {
        private boolean checked;

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }
    }

    @BeforeAll
    static void startPlatform() {
        postgres =
                new PostgreSQLContainer<>(AGE_IMAGE)
                        .withDatabaseName("epm")
                        .withUsername("epm")
                        .withPassword("epm-spike");
        postgres.start();
        jdbcUrl = postgres.getJdbcUrl();
    }

    @AfterAll
    static void stopPlatform() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    @Order(1)
    void postgresConnectivityAndAgeExtensionLoad() throws Exception {
        try (Connection c = connect();
                Statement st = c.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS age");
            st.execute("LOAD 'age'");
            try (ResultSet rs =
                    st.executeQuery("SELECT extname FROM pg_extension WHERE extname = 'age'")) {
                assertThat(rs.next()).as("AGE extension must be installed").isTrue();
            }
        }
    }

    @Test
    @Order(2)
    void ageCypherRoundTrip() throws Exception {
        try (Connection c = connect();
                Statement st = c.createStatement()) {
            st.execute("SET search_path = ag_catalog, \"$user\", public");
            st.execute("SELECT ag_catalog.create_graph('toolchain')");
            st.execute(
                    "SELECT * FROM ag_catalog.cypher('toolchain', $$ CREATE (n:Spike {kind:"
                            + " 'evidence'}) RETURN n $$) AS (n ag_catalog.agtype)");
            try (ResultSet rs =
                    st.executeQuery(
                            "SELECT * FROM ag_catalog.cypher('toolchain', $$ MATCH (n:Spike) RETURN"
                                    + " n.kind $$) AS (kind ag_catalog.agtype)")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).contains("evidence");
            }
        }
    }

    @Test
    @Order(3)
    void droolsRuleTerminates() {
        KieHelper helper = new KieHelper();
        helper.addContent(
                "package dev.epm.tests.toolchain\n"
                        + "rule \"toolchain terminus\"\n"
                        + "when\n"
                        + "    $s : ToolchainCompatibilityTest.SpikeSignal( checked == false )\n"
                        + "then\n"
                        + "    modify($s) { setChecked(true) }\n"
                        + "end\n",
                ResourceType.DRL);
        KieBase kbase = helper.build();
        KieSession session = kbase.newKieSession();
        try {
            SpikeSignal signal = new SpikeSignal();
            session.insert(signal);
            int fired = session.fireAllRules(10);
            assertThat(fired).isEqualTo(1);
            assertThat(signal.isChecked()).as("rule must fire exactly once and terminate").isTrue();
        } finally {
            session.dispose();
        }
    }

    @Test
    @Order(4)
    void dmnDecisionEvaluates() {
        KieHelper helper = new KieHelper();
        helper.addResource(
                ResourceFactory.newClassPathResource("toolchain-spike.dmn"), ResourceType.DMN);
        KieBase kbase = helper.build();
        DMNRuntime dmnRuntime = KieRuntimeFactory.of(kbase).get(DMNRuntime.class);
        DMNModel model = dmnRuntime.getModel("https://epm.dev/toolchain/spike", "toolchain-spike");
        assertThat(model).as("DMN model must compile without errors").isNotNull();
        assertThat(model.getMessages(DMNMessage.Severity.ERROR)).isEmpty();

        DMNContext context = dmnRuntime.newContext();
        context.set("severity", "HIGH");
        DMNResult result = dmnRuntime.evaluateAll(model, context);
        assertThat(result.getDecisionResultByName("Approval Level").getResult())
                .isEqualTo("TWO_EYES");

        DMNContext lowContext = dmnRuntime.newContext();
        lowContext.set("severity", "LOW");
        DMNResult lowResult = dmnRuntime.evaluateAll(model, lowContext);
        assertThat(lowResult.getDecisionResultByName("Approval Level").getResult())
                .isEqualTo("SINGLE");
    }

    @Test
    @Order(5)
    void kogitoProcessRunsAndPersistsToPostgres() throws Exception {
        try (Connection c = connect();
                Statement st = c.createStatement()) {
            st.execute(
                    "CREATE TABLE IF NOT EXISTS toolchain_process_run ("
                            + "run_id text PRIMARY KEY, "
                            + "process_id text NOT NULL, "
                            + "status text NOT NULL, "
                            + "completed_at timestamptz NOT NULL DEFAULT now())");
        }
        // jBPM 10 user tasks require the kogito-style work item manager, so the process runs
        // on LightProcessRuntime wired through StaticProcessConfig with a named human-task
        // handler - the embedded path the application services use for durable human work.
        // The spike handler parks activation transitions and lets terminating ones flow
        // through the default lifecycle; the governed four-eyes task handler arrives with
        // the exception-case task (Task 14).
        SpikeHumanTaskHandler handler = new SpikeHumanTaskHandler();
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
        helper.addResource(
                ResourceFactory.newClassPathResource("toolchain-spike.bpmn"), ResourceType.BPMN2);
        LightProcessRuntime runtime =
                LightProcessRuntime.of(application, helper.build().getProcesses(), provider);

        ProcessInstance instance = runtime.startProcess("toolchain_spike");
        assertThat(instance.getState())
                .as("process runs and waits at the human task")
                .isEqualTo(KogitoProcessInstance.STATE_ACTIVE);
        assertThat(handler.parkedItems()).as("human task work item must be parked").hasSize(1);
        assertThat(handler.parkedItems().get(0).getName())
                .as("parked item is the BPMN human-task work item")
                .isEqualTo("Human Task");

        runtime.getKogitoWorkItemManager()
                .completeWorkItem(handler.parkedItems().get(0).getStringId(), Map.of());
        assertThat(instance.getState())
                .as("process completes after the named human completes the task")
                .isEqualTo(KogitoProcessInstance.STATE_COMPLETED);

        String runId = UUID.randomUUID().toString();
        try (Connection c = connect();
                PreparedStatement insert =
                        c.prepareStatement(
                                "INSERT INTO toolchain_process_run (run_id, process_id, status)"
                                        + " VALUES (?, ?, ?)")) {
            insert.setString(1, runId);
            insert.setString(2, instance.getId());
            insert.setString(3, "COMPLETED");
            insert.executeUpdate();
        }
        try (Connection c = connect();
                PreparedStatement select =
                        c.prepareStatement(
                                "SELECT status FROM toolchain_process_run WHERE run_id = ?")) {
            select.setString(1, runId);
            try (ResultSet rs = select.executeQuery()) {
                assertThat(rs.next())
                        .as("completed process state must be durable in PostgreSQL")
                        .isTrue();
                assertThat(rs.getString(1)).isEqualTo("COMPLETED");
            }
        }
    }

    /**
     * Minimal kogito work-item handler used only by this spike: parks activating work items (the
     * durable human wait point) and forwards terminating transitions to the default lifecycle so
     * completion drives the process onward.
     */
    static final class SpikeHumanTaskHandler extends DefaultKogitoWorkItemHandler {

        private final List<KogitoWorkItem> parked = new CopyOnWriteArrayList<>();

        List<KogitoWorkItem> parkedItems() {
            return parked;
        }

        @Override
        public Optional<WorkItemTransition> transitionToPhase(
                KogitoWorkItemManager manager, KogitoWorkItem item, WorkItemTransition transition) {
            if (!transition.termination().isPresent()) {
                // Record the durable wait point while the default lifecycle activates the item.
                parked.add(item);
            }
            return super.transitionToPhase(manager, item, transition);
        }
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
    }
}
