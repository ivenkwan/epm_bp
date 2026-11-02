package dev.epm.admin;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminServiceTest {

    @Test
    void healthResourceResponds() {
        RestAssured.when().get("/api/v1/admin/health").then().statusCode(200);
    }
}
