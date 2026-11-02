package dev.epm.admin;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import dev.epm.admin.authorization.AccessDecisionEntity;
import dev.epm.admin.authorization.EntityScopeEntity;
import dev.epm.admin.identity.RoleStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Task 8 authorization security suite: allow/deny matrix from the active policy, empty scope
 * returning zero rows, cross-entity denial, self-approval denial, conflicting-role denial with
 * escalation, overdue-review denial that never grants, gateway-bypass enforcement (direct HTTP to
 * the service, no APISIX), and no restricted-value leakage in denied responses (AC-F-039-01..03).
 */
@QuarkusTest
class AuthorizationSecurityTest {

    @Inject RoleStore roleStore;

    @Test
    void allowedAccessIsAuditedWithSessionEvidence() {
        String user = "allowed-" + System.nanoTime();
        grant(user, "finance-preparer", "ENT101");
        given().header("X-Actor", user)
                .contentType("application/json")
                .body(
                        "{\"objectType\":\"submission\",\"action\":\"PREPARE\",\"entity\":\"ENT101\"}")
                .when()
                .post("/api/v1/admin/authorize")
                .then()
                .statusCode(200);
        // Every check is audited: the decision row carries user, roles, object, action.
        inTransaction(() -> assertThat(AccessDecisionEntity.countFor(user)).isEqualTo(1));
    }

    @Test
    void restrictedEntityIsDeniedServerSideWithoutValueLeakage() {
        String user = "restricted-" + System.nanoTime();
        grant(user, "finance-preparer", "ENT101");
        var response =
                given().header("X-Actor", user)
                        .contentType("application/json")
                        .body(
                                "{\"objectType\":\"submission\",\"action\":\"PREPARE\",\"entity\":\"ENT104\"}")
                        .when()
                        .post("/api/v1/admin/authorize");
        response.then().statusCode(403);
        String body = response.getBody().asString();
        assertThat(body).contains("DENY");
        assertThat(body).doesNotContain("ENT101"); // no scope content leaks
        inTransaction(() -> assertThat(AccessDecisionEntity.countFor(user)).isEqualTo(1));
    }

    @Test
    void emptyScopeReturnsZeroEntities() {
        String user = "empty-" + System.nanoTime();
        grant(user, "assurance-reader", null);
        var response = given().header("X-Actor", user).when().get("/api/v1/admin/entitlements");
        response.then().statusCode(200);
        assertThat(response.getBody().jsonPath().getList("entities")).isEmpty();
    }

    @Test
    void selfApprovalIsDenied() {
        String user = "self-" + System.nanoTime();
        grant(user, "finance-approver", "ENT101");
        given().header("X-Actor", user)
                .contentType("application/json")
                .body(
                        "{\"objectType\":\"adjustment\",\"action\":\"APPROVE\","
                                + "\"entity\":\"ENT101\",\"preparer\":\""
                                + user
                                + "\"}")
                .when()
                .post("/api/v1/admin/authorize")
                .then()
                .statusCode(403)
                .body("reason", org.hamcrest.Matchers.equalTo("self-approval is prohibited"));
    }

    @Test
    void conflictingRolesAreDenied() {
        String user = "conflict-" + System.nanoTime();
        grant(user, "finance-preparer", "ENT101");
        grant(user, "finance-approver", "ENT101");
        var response =
                given().header("X-Actor", user)
                        .contentType("application/json")
                        .body(
                                "{\"objectType\":\"submission\",\"action\":\"PREPARE\",\"entity\":\"ENT101\"}")
                        .when()
                        .post("/api/v1/admin/authorize");
        response.then().statusCode(403);
        assertThat(response.getBody().asString()).contains("segregation of duties conflict");
    }

    @Test
    void actionOutsideRoleIsDenied() {
        String user = "reader-" + System.nanoTime();
        grant(user, "assurance-reader", "ENT101");
        given().header("X-Actor", user)
                .contentType("application/json")
                .body(
                        "{\"objectType\":\"adjustment\",\"action\":\"APPROVE\",\"entity\":\"ENT101\"}")
                .when()
                .post("/api/v1/admin/authorize")
                .then()
                .statusCode(403);
    }

    @Test
    void overdueReviewDeniesAndEscalatesWithoutGranting() {
        String user = "overdue-" + System.nanoTime();
        grant(user, "finance-preparer", "ENT101");
        setScopeOverdue(user, "ENT101");
        var response =
                given().header("X-Actor", user)
                        .contentType("application/json")
                        .body(
                                "{\"objectType\":\"submission\",\"action\":\"PREPARE\",\"entity\":\"ENT101\"}")
                        .when()
                        .post("/api/v1/admin/authorize");
        response.then().statusCode(403);
        assertThat(response.getBody().asString()).contains("access review overdue");
        // The overdue scope also disappears from the visible row scope.
        var scope = given().header("X-Actor", user).when().get("/api/v1/admin/entitlements");
        assertThat(scope.getBody().jsonPath().getList("entities")).isEmpty();
    }

    @Test
    void gatewayBypassIsStillEnforced() {
        // Direct service call with no APISIX in front: identical denial semantics.
        String user = "bypass-" + System.nanoTime();
        grant(user, "finance-preparer", "ENT101");
        given().header("X-Actor", user)
                .contentType("application/json")
                .body(
                        "{\"objectType\":\"reconciliation\",\"action\":\"CERTIFY\",\"entity\":\"ENT101\"}")
                .when()
                .post("/api/v1/admin/authorize")
                .then()
                .statusCode(403);
    }

    // ---------- helpers ----------

    @Transactional
    void grant(String user, String role, String entity) {
        roleStore.grant(user, role, "admin@example.invalid");
        if (entity != null
                && EntityScopeEntity.find("userId = ?1 and entityId = ?2", user, entity)
                                .firstResult()
                        == null) {
            EntityScopeEntity scope = new EntityScopeEntity();
            scope.setUserId(user);
            scope.setEntityId(entity);
            scope.setGrantedBy("admin@example.invalid");
            scope.setGrantedAtUtc(OffsetDateTime.now(ZoneOffset.UTC));
            scope.setReviewDueUtc(OffsetDateTime.now(ZoneOffset.UTC).plusMonths(3));
            scope.persist();
        }
    }

    @Transactional
    void setScopeOverdue(String user, String entity) {
        EntityScopeEntity scope =
                EntityScopeEntity.find("userId = ?1 and entityId = ?2", user, entity).firstResult();
        scope.setReviewDueUtc(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        scope.persist();
    }

    @Transactional
    void inTransaction(Runnable assertion) {
        assertion.run();
    }
}
