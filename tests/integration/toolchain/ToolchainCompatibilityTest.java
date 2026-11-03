package org.kie.kogito.toolchain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.RuleUnits;
import org.junit.jupiter.api.Test;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.internal.utils.DMNRuntimeBuilder;
import org.kie.kogito.Model;
import org.kie.kogito.process.Process;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.Processes;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@QuarkusTest
@QuarkusTestResource(ToolchainCompatibilityTest.AgePostgresResource.class)
public class ToolchainCompatibilityTest {

  public static final String AGE_IMAGE =
      "apache/age:release_PG16_1.6.0@sha256:16aa423d20a31aed36a3313244bf7aa00731325862f20ed584510e381f2feaed";

  @jakarta.inject.Inject DataSource dataSource;

  @jakarta.inject.Inject Processes processes;

  @jakarta.inject.Inject RuleUnits ruleUnits;

  @Test
  void postgresqlConnects() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT 1")) {
      assertTrue(resultSet.next());
      assertEquals(1, resultSet.getInt(1));
    }
  }

  @Test
  void ageExtensionLoads() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE EXTENSION IF NOT EXISTS age");
      statement.execute("LOAD 'age'");
      try (ResultSet resultSet =
          statement.executeQuery("SELECT extversion FROM pg_extension WHERE extname = 'age'")) {
        assertTrue(resultSet.next());
        assertEquals("1.6.0", resultSet.getString(1));
      }
    }
  }

  @Test
  void droolsRuleTerminates() {
    ToolchainUnit unit = new ToolchainUnit();
    unit.getStrings().add("start");
    try (RuleUnitInstance<ToolchainUnit> instance =
        ruleUnits.create(ToolchainUnit.class).createInstance(unit)) {
      assertEquals(1, instance.fire());
      assertEquals(1, instance.executeQuery("finished").toList().size());
    }
  }

  @Test
  void dmnDecisionEvaluates() {
    DMNRuntime runtime =
        DMNRuntimeBuilder.fromDefaults()
            .buildConfiguration()
            .fromClasspathResource("dmn/smoke.dmn", getClass())
            .getOrElseThrow(error -> new IllegalStateException("DMN initialization failed", error));
    DMNModel model = runtime.getModel("https://kie.apache.org/dmn/toolchain", "toolchain");
    DMNContext context = runtime.newContext();
    DMNResult result = runtime.evaluateAll(model, context);
    assertFalse(result.hasErrors());
    assertEquals("COMPATIBLE", result.getDecisionResultByName("status").getResult());
  }

  @Test
  void kogitoProcessPersists() throws Exception {
    Process<? extends Model> process = processes.processById("toolchain_smoke");
    Model model = process.createModel();
    ProcessInstance<? extends Model> instance = process.createInstance(model);
    instance.start();
    assertEquals(ProcessInstance.STATE_ACTIVE, instance.status());
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM process_instances WHERE process_id = ? AND id = ?")) {
      statement.setString(1, "toolchain_smoke");
      statement.setString(2, instance.id());
      try (ResultSet resultSet = statement.executeQuery()) {
        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getInt(1));
      }
    } finally {
      instance.abort();
    }
  }

  public static class AgePostgresResource implements QuarkusTestResourceLifecycleManager {
    private PostgreSQLContainer<?> postgres;

    @Override
    public Map<String, String> start() {
      postgres =
          new PostgreSQLContainer<>(
                  DockerImageName.parse(AGE_IMAGE).asCompatibleSubstituteFor("postgres"))
              .withDatabaseName("kogito")
              .withUsername("synthetic")
              .withPassword("synthetic-only");
      postgres.start();
      return Map.of(
          "kogito.persistence.type", "jdbc",
          "kogito.generate.rest", "false",
          "kie.flyway.enabled", "true",
          "quarkus.datasource.db-kind", "postgresql",
          "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
          "quarkus.datasource.username", postgres.getUsername(),
          "quarkus.datasource.password", postgres.getPassword(),
          "quarkus.datasource.devservices.enabled", "false",
          "quarkus.kogito.devservices.enabled", "false",
          "quarkus.log.level", "WARNING");
    }

    @Override
    public void stop() {
      if (postgres != null) {
        postgres.stop();
      }
    }
  }
}
