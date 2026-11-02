# Version Matrix

Locked toolchain for the R0 foundation. Every row was verified on this repository before feature
work began; the executing proof is `tests/integration/.../ToolchainCompatibilityTest.java`, which
starts a real PostgreSQL/AGE container, loads the AGE extension, runs a cypher round-trip, fires a
terminating Drools rule, evaluates a DMN decision table, and executes a jBPM/Kogito BPMN process
with a human task whose completed state is persisted to PostgreSQL.

Verification run: 2026-11-02, `./mvnw -pl tests/integration test -Dtest=ToolchainCompatibilityTest`
→ `Tests run: 5, Failures: 0, Errors: 0`.

| Component | Locked version | Where pinned | Verified by |
|---|---|---|---|
| Java (LTS) | Temurin 21.0.13+9 (`[21,22)` enforced) | `pom.xml` enforcer `requireJavaVersion` | full reactor build |
| Maven distribution | 3.9.11 (wrapper) | `mvnw`, `.mvn/wrapper/maven-wrapper.properties` | `./mvnw -version` |
| Quarkus platform | 3.15.7 (LTS line) | `pom.xml` `quarkus.platform.version` | reactor build, service smoke tests |
| Drools (rules + compiler) | 10.2.0 (`org.drools`) | `pom.xml` `kie.version` | spike test 3 (terminating rule) |
| DMN runtime | 10.2.0 (`org.kie.kogito:kogito-dmn`) | `pom.xml` `kie.version` | spike test 4 (decision table evaluation) |
| jBPM/Kogito process runtime | 10.2.0 (`jbpm-flow`, `jbpm-bpmn2`, `process-workitems`, `jbpm-usertask[-workitem]`) | `pom.xml` `kie.version` | spike test 5 (BPMN process, human task, PG persistence) |
| KieHelper / ResourceFactory | `org.kie.kogito:kogito-api` 10.2.0, `org.kie:kie-internal` 10.2.0 | `pom.xml` | compile + spike |
| PostgreSQL | 17 (image below) | container/K8s manifests | spike tests 1–2, 5 |
| Apache AGE | 1.7.0 on PG17 | container image | spike test 2 (cypher round-trip) |
| PostgreSQL + AGE image | `apache/age:release_PG17_1.7.0` @ `sha256:92a5d223965bc2e436f9eee436e0bd2c0d81f3b59124b3d197ec94706f3450a8` | Testcontainers, `deploy/kubernetes/base` | spike tests |
| APISIX | 3.13.0-debian @ `sha256:c5c7a55ebb5c07abc210dbb963a37f41030e12c91d23bacedbaa168fec633bd7` | `deploy/apisix` | image pull + deploy (Task 4) |
| OIDC test provider (Keycloak) | 26.8 @ `sha256:3f3c5f8e03c614774b4bd9173b88391fefa4153bfc010d8012d59ad825377332` | `deploy/kubernetes/base` | image pull + deploy (Task 4) |
| OpenTelemetry collector | 0.116.0 @ `sha256:91d0e8dcd0cf4c0f2b1002a3be5979dfb20be235af96ef51063164548e2ccff6` | `deploy/observability` | image pull + deploy (Task 4) |
| Node.js (LTS) | 22.22.1 | CI setup-node, local dev | pnpm workspace install/build |
| pnpm | 10.34.5 | `package.json` packageManager | lockfile generation, builds |
| JUnit | 5.11.3 | `pom.xml` BOM import | all tests |
| Testcontainers | 1.21.4 | `pom.xml` BOM import | spike container lifecycle |
| PostgreSQL JDBC | 42.7.4 | `pom.xml` | spike tests |
| AssertJ | 3.26.3 | `pom.xml` | all tests |
| google-java-format (Spotless) | 1.19.2 (AOSP) | `pom.xml` spotless config | `spotless:check` in build |
| CycloneDX SBOM | plugin 2.8.2, schema 1.5 | `pom.xml` | aggregate BOM at package |

## Dependency-resolution notes

1. **BOM import order matters.** JUnit and Testcontainers BOMs are imported *before* the Quarkus
   platform BOM because the platform pins Testcontainers 1.20.1, whose docker-java client negotiates
   Docker Engine API 1.32; Docker 29+ daemons reject API versions below 1.40. Testcontainers 1.21.4
   (docker-java 3.4.2) negotiates correctly against Docker 29.5.3 (server API 1.54).
2. **Kogito Quarkus extensions are retired.** `org.kie.kogito:kogito-quarkus-*` ended at 1.44.1.Final
   (built for Quarkus 3.8/3.10). The maintained KIE 10.x train ships plain-Java runtimes
   (`org.drools` 10.2.0, `org.kie.kogito` 10.2.0). This platform therefore embeds Drools/DMN/jBPM as
   governed libraries behind finance-core's versioned harness instead of coupling to retired
   Quarkus extensions. Upgrade constraint: moving past Quarkus 3.15 LTS is independent of the KIE
   train; moving KIE versions requires re-running the toolchain spike.
3. **KIE 10 package moves.** `KieHelper` is `org.kie.internal.utils.KieHelper`, `ResourceFactory` is
   `org.kie.internal.io.ResourceFactory`, and `ProcessInstance.getId()` returns `String`. Code and
   tests reflect this; do not copy KIE 7.x snippets.
4. **jBPM 10 user tasks need the kogito work-item manager.** A classic `kbase.newKieSession()`
   uses `DefaultWorkItemManager`, which cannot be cast to `KogitoWorkItemManager` and fails any
   work-item node with `STATE_ERROR`. Processes with human tasks must run on
   `LightProcessRuntime` wired through `StaticProcessConfig` (see the spike). The full kogito
   user-task lifecycle (`jbpm-usertask` + `UserTaskKogitoWorkItemHandler`) additionally requires
   `UserTasks`/`UserTaskConfig` engines on the application; the spike uses a minimal parking handler
   and the governed four-eyes handler lands with Task 14.
5. **`requireUpperBoundDeps` is deliberately not enforced.** The Quarkus platform BOM manages its own
   soft version conflicts; the BOM import in `dependencyManagement` is the enforced pinning
   mechanism. `requireMavenVersion`, `requireJavaVersion`, and `banDuplicatePomDependencyVersions`
   are enforced; `finance-core` marks its Quarkus runtime dependencies `provided` so consuming
   services declare their own platform dependencies.

## Supported platforms

- Development/CI: Linux x86_64, Docker Engine 29.x with API ≥ 1.44, Java 21, Node 22.
- Kubernetes: any conformant cluster able to run the pinned images (verified on kind 0.33.0 /
  Kubernetes 1.37.1 client; Task 4 records the distributions actually exercised).

## Upgrade checklist (change procedure)

1. Update the version property or image digest in this matrix and the owning manifest together.
2. Re-run `./mvnw -pl tests/integration test -Dtest=ToolchainCompatibilityTest`.
3. Run the full regression commands from `todo.md` Execution Rules.
4. Record the new verification date and result in the table above.
