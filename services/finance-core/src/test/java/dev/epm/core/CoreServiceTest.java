package dev.epm.core;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CoreServiceTest {

    @Test
    void healthResourceResponds() {
        RestAssured.when().get("/api/v1/core/health").then().statusCode(200);
    }
}
