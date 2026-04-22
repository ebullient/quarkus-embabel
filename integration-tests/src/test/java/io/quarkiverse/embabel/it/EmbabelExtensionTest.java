package io.quarkiverse.embabel.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test to verify the Embabel Agent extension loads correctly
 * and the application starts successfully.
 */
@QuarkusTest
public class EmbabelExtensionTest {

    @Test
    public void testEmbabelEndpoint() {
        given()
                .when().get("/embabel")
                .then()
                .statusCode(200)
                .body(is("Embabel Agent extension is loaded"));
    }
}