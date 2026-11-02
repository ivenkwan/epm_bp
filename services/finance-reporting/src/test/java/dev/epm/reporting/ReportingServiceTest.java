package dev.epm.reporting;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ReportingServiceTest {

    @Test
    void healthResourceResponds() {
        RestAssured.when().get("/api/v1/reporting/health").then().statusCode(200);
    }
}
