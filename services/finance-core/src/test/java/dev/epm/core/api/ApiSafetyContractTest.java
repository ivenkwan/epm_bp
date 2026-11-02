package dev.epm.core.api;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Task 6 API-safety contract suite (executable location: finance-core test sources; the black-box
 * HTTP assertions are contract-level). The durable 202-job contract is deferred behind blocker O-B6
 * and covered when jobs land.
 */
@QuarkusTest
class ApiSafetyContractTest {

    private static final String BASE = "/api/v1/core/submissions";

    private static String validBody() {
        return "{\"effectiveDate\":\"2026-10-31\",\"currency\":\"USD\","
                + "\"openingCash\":\"1250000.00\","
                + "\"sourceContractId\":\"treasury-daily-v3\","
                + "\"entityId\":\"ENT101\"}";
    }

    @Test
    void sameKeySamePayloadReplayReturnsOriginalResult() {
        String key = UUID.randomUUID().toString();
        var first =
                given().header("Idempotency-Key", key)
                        .header("X-Actor", "preparer@example.invalid")
                        .contentType("application/json")
                        .body(validBody())
                        .when()
                        .post(BASE)
                        .then()
                        .statusCode(201);
        String firstBody = first.extract().body().asString();

        var replay =
                given().header("Idempotency-Key", key)
                        .header("X-Actor", "preparer@example.invalid")
                        .contentType("application/json")
                        .body(validBodyWithDifferentKeyOrder())
                        .when()
                        .post(BASE)
                        .then()
                        .statusCode(201)
                        .header("Idempotency-Replayed", "true");
        org.assertj.core.api.Assertions.assertThat(replay.extract().body().asString())
                .isEqualTo(firstBody);
    }

    @Test
    void sameKeyChangedPayloadIsRejectedWith409() {
        String key = UUID.randomUUID().toString();
        given().header("Idempotency-Key", key)
                .header("X-Actor", "preparer@example.invalid")
                .contentType("application/json")
                .body(validBody())
                .when()
                .post(BASE)
                .then()
                .statusCode(201);

        given().header("Idempotency-Key", key)
                .header("X-Actor", "preparer@example.invalid")
                .contentType("application/json")
                .body(validBody().replace("1250000.00", "999.00"))
                .when()
                .post(BASE)
                .then()
                .statusCode(409)
                .contentType("application/problem+json")
                .body("code", org.hamcrest.Matchers.equalTo("IDEMPOTENCY_KEY_REUSED"))
                .body("correlationId", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void staleIfMatchIsRejectedWith409WithoutSecondEffect() {
        String id = createSubmission();

        // First update advances revision 1 -> 2 with the correct If-Match.
        given().header("If-Match", "\"revision-1\"")
                .header("X-Actor", "preparer@example.invalid")
                .contentType("application/json")
                .body(validBody())
                .when()
                .put(BASE + "/" + id)
                .then()
                .statusCode(200);

        // The old If-Match must now fail without creating revision 3.
        given().header("If-Match", "\"revision-1\"")
                .header("X-Actor", "preparer@example.invalid")
                .contentType("application/json")
                .body(validBody())
                .when()
                .put(BASE + "/" + id)
                .then()
                .statusCode(409)
                .contentType("application/problem+json")
                .body("code", org.hamcrest.Matchers.equalTo("STALE_REVISION"));

        given().when()
                .get(BASE + "/" + id)
                .then()
                .statusCode(200)
                .header("ETag", "\"revision-2\"")
                .body("revision", org.hamcrest.Matchers.equalTo(2));
    }

    @Test
    void validationFailureIsSafeProblemJsonWithViolations() {
        given().header("Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Actor", "preparer@example.invalid")
                .contentType("application/json")
                .body(
                        "{\"effectiveDate\":\"31-10-2026\",\"currency\":\"usd\",\"openingCash\":\"1.234\"}")
                .when()
                .post(BASE)
                .then()
                .statusCode(422)
                .contentType("application/problem+json")
                .body("code", org.hamcrest.Matchers.equalTo("VALIDATION_FAILED"))
                .body("violations.size()", org.hamcrest.Matchers.greaterThanOrEqualTo(2))
                .body("correlationId", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void correlationIdIsPropagatedAndEchoed() {
        given().header("X-Correlation-Id", "corr-contract-0001")
                .when()
                .get(BASE + "/does-not-exist")
                .then()
                .statusCode(404)
                .header("X-Correlation-Id", "corr-contract-0001")
                .contentType("application/problem+json");
    }

    @Test
    void f044ExcludedActionsHaveNoRoutes() {
        for (String excluded :
                new String[] {
                    "sql",
                    "cypher",
                    "drl",
                    "dmn-exec",
                    "bpmn",
                    "payments",
                    "gl-postings",
                    "autonomous-approvals"
                }) {
            given().contentType("application/json")
                    .body("{}")
                    .when()
                    .post("/api/v1/core/" + excluded)
                    .then()
                    .statusCode(404);
        }
    }

    private String createSubmission() {
        return given().header("Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Actor", "preparer@example.invalid")
                .contentType("application/json")
                .body(validBody())
                .when()
                .post(BASE)
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getString("submissionId");
    }

    private static String validBodyWithDifferentKeyOrder() {
        // Same semantic payload with a different key order proves canonical fingerprints.
        return "{\"openingCash\":\"1250000.00\",\"currency\":\"USD\","
                + "\"entityId\":\"ENT101\",\"sourceContractId\":\"treasury-daily-v3\","
                + "\"effectiveDate\":\"2026-10-31\"}";
    }
}
