package io.quarkiverse.embabel.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test to verify that Ai-related beans can be injected via CDI.
 * <p>
 * This test verifies that the AiBeansProducer correctly provides:
 * <ul>
 * <li>Ai - Direct access to AI operations</li>
 * <li>ExecutingOperationContext - Full operation context</li>
 * <li>AiBuilder - Builder for creating custom Ai instances</li>
 * </ul>
 * <p>
 * These beans mirror Spring Boot's InfrastructureInjectionConfiguration
 * and enable components to inject Ai functionality without being agents.
 */
@QuarkusTest
public class AiInjectionTest {

    @Test
    public void testAiBeansAreInjectable() {
        given()
                .when().get("/ai-injection/status")
                .then()
                .statusCode(200)
                .body(is("SUCCESS: All Ai beans injected"));
    }

    @Test
    public void testAiBeanInfo() {
        String beanInfo = given()
                .when().get("/ai-injection/info")
                .then()
                .statusCode(200)
                .extract().asString();

        assertThat(beanInfo)
                .as("Bean info should confirm all beans are injected")
                .contains("Ai injected: true")
                .contains("ExecutingOperationContext injected: true")
                .contains("AiBuilder injected: true");

        System.out.println("=== Ai Bean Injection Info ===");
        System.out.println(beanInfo);
    }
}