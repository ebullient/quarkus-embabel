package io.quarkiverse.embabel.it.agent;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration test for WriteAndReviewAgent using Quarkus Test.
 * <p>
 * This test verifies that the Quarkus Embabel extension properly:
 * <ul>
 * <li>Loads the extension and starts the application</li>
 * <li>Registers CDI beans (ModelProvider, AgentPlatform, agents)</li>
 * <li>Discovers and deploys @Agent classes</li>
 * <li>Enables single-step agent invocation (Story)</li>
 * <li>Enables multi-step agent invocation with action chaining (ReviewedStory)</li>
 * </ul>
 * <p>
 * Uses WireMock to mock OpenAI API calls, avoiding real API requests in tests.
 */
@QuarkusTest
class StoryAgentIntegrationTest {

    @Test
    void shouldCraftStoryViaRestEndpoint() {
        // When: Request Story goal - framework executes craftStory() action
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
    void shouldCraftAndReviewStoryViaRestEndpoint() {
        // When: Request ReviewedStory goal - framework chains craftStory() → reviewStory()
        //
        // This test demonstrates multi-step agent invocation where the framework
        // automatically chains actions: UserInput → craftStory() → Story → reviewStory() → ReviewedStory
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "prompt": "Write a mystery story set in Victorian London"
                        }
                        """)
                .when()
                .post("/story/review")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                // Verify ReviewedStory structure
                .body("story", notNullValue())
                .body("story.text", notNullValue())
                .body("review", notNullValue())
                .body("reviewer", notNullValue())
                .body("reviewer.name", containsString("Review"));
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

    @Test
    void shouldHandleSimplePromptForReview() {
        // When: Request review with simple prompt
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "prompt": "Adventure"
                        }
                        """)
                .when()
                .post("/story/review")
                .then()
                .statusCode(200)
                .body("story.text", notNullValue())
                .body("review", notNullValue());
    }
}
