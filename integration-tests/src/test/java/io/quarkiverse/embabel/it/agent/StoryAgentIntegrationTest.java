package io.quarkiverse.embabel.it.agent;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration test for StoryAgent using Quarkus Test.
 * <p>
 * This test verifies that the Quarkus Embabel extension properly:
 * - Loads the extension and starts the application
 * - Registers CDI beans (ModelProvider, AgentPlatform)
 * - Enables agent invocation through REST endpoints
 * <p>
 * Uses WireMock to mock OpenAI API calls, avoiding real API requests in tests.
 */
@QuarkusTest
class StoryAgentIntegrationTest {

    @Test
    void shouldCraftStoryViaRestEndpoint() {
        // When: Call the REST endpoint
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "prompt": "Tell me a story about a magical kingdom"
                        }
                        """)
                .when()
                .post("/story/craft")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("text", notNullValue())
                .body("text", containsString("magical kingdom"));
    }

    @Test
    void shouldHandleEmptyPrompt() {
        // When: Call with empty prompt
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "prompt": ""
                        }
                        """)
                .when()
                .post("/story/craft")
                .then()
                .statusCode(200)
                .body("text", notNullValue());
    }
}
