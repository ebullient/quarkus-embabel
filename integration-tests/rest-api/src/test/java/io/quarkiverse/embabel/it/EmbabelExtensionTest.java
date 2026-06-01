package io.quarkiverse.embabel.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
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

    /**
     * Tests that Embabel configuration properties are properly bound and injected
     * through to runtime beans (AgentPlatform, ToolLoopConfiguration, etc.).
     * <p>
     * This verifies the full configuration flow:
     * <ol>
     * <li>Properties from application.properties (test resources)</li>
     * <li>Bound to Embabel config classes via quarkus-spring-boot-properties</li>
     * <li>Injected into CDI producers</li>
     * <li>Available in runtime beans</li>
     * </ol>
     * <p>
     * Configuration values are defined in {@code src/test/resources/application.properties}.
     */
    @Test
    public void testConfigurationBindingAndInjection() {
        given()
                .when().get("/embabel/config")
                .then()
                .statusCode(200)
                .body("platformNameFromConfig", equalTo("test-platform"))
                .body("platformNameFromBean", equalTo("test-platform"))
                .body("platformDescription", equalTo("Integration Test Platform"))
                .body("toolLoopType", equalTo("PARALLEL"))
                .body("toolLoopMaxIterations", equalTo(3))
                .body("defaultLlm", equalTo("gpt-4o-mini"))
                .body("defaultEmbeddingModel", equalTo("text-embedding-ada-002"));

        System.out.println("✓ Configuration successfully flows: properties → config classes → runtime beans");
    }
}
