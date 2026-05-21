package io.quarkiverse.embabel.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test for EmbeddingService using Quarkus Test.
 * <p>
 * This test verifies that the Quarkus Embabel extension properly:
 * <ul>
 * <li>Registers EmbeddingService via ModelProvider</li>
 * <li>Wraps LangChain4j EmbeddingModel correctly</li>
 * <li>Converts between LangChain4j Embedding and float[] format</li>
 * <li>Handles WireMock-mocked OpenAI embedding API calls</li>
 * </ul>
 * <p>
 * Uses WireMock to mock OpenAI embeddings API, avoiding real API requests.
 */
@QuarkusTest
class EmbeddingIntegrationTest {

    @Test
    void shouldGenerateEmbeddingViaRestEndpoint() {
        // When: Request embedding for text
        given()
                .queryParam("text", "Hello, world!")
                .when()
                .get("/embedding/embed")
                .then()
                .statusCode(200)
                .body("text", equalTo("Hello, world!"))
                .body("embedding", notNullValue())
                .body("dimensions", equalTo(8)); // Matches WireMock mock (8 dimensions)
    }

    @Test
    void shouldHandleEmptyText() {
        // When: Request embedding for empty text (query param comes through as null when empty)
        given()
                .queryParam("text", "")
                .when()
                .get("/embedding/embed")
                .then()
                .statusCode(200)
                .body("text", equalTo(null)) // Empty query param is null
                .body("embedding", notNullValue())
                .body("dimensions", equalTo(8));
    }

    @Test
    void shouldHandleLongerText() {
        // When: Request embedding for longer text
        String longText = "This is a longer piece of text that should still be embedded correctly by the service.";
        given()
                .queryParam("text", longText)
                .when()
                .get("/embedding/embed")
                .then()
                .statusCode(200)
                .body("text", equalTo(longText))
                .body("embedding", notNullValue())
                .body("dimensions", equalTo(8));
    }
}