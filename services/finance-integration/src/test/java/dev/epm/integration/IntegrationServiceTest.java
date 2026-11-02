package dev.epm.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

@QuarkusTest
class IntegrationServiceTest {

    @Test
    void healthResourceResponds() {
        RestAssured.when().get("/api/v1/integration/health").then().statusCode(200);
    }
}
