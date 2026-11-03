package dev.epm.planning;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PlanningServiceTest {

    @Test
    void healthResourceResponds() {
        RestAssured.when().get("/api/v1/planning/health").then().statusCode(200);
    }
}
