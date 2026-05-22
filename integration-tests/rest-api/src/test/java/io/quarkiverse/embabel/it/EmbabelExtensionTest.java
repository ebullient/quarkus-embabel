package io.quarkiverse.embabel.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    public void testModelProviderEndpoint() {
        String modelInfo = given()
                .when().get("/embabel/models")
                .then()
                .statusCode(200)
                .extract().asString();

        assertThat(modelInfo)
                .as("Model info should contain registered LLM and embedding configuration")
                .contains("gpt-4o-mini", "openai", "text-embedding-ada-002");

        System.out.println("=== Available Models ===");
        System.out.println(modelInfo);
    }

    @Test
    public void testDefaultEmbeddingEndpoint() {
        String embeddingInfo = given()
                .when().get("/embabel/embeddings/default")
                .then()
                .statusCode(200)
                .extract().asString();

        assertThat(embeddingInfo)
                .as("Default embedding service should be resolved from the model provider")
                .isEqualTo("openai:text-embedding-ada-002");
    }
}
