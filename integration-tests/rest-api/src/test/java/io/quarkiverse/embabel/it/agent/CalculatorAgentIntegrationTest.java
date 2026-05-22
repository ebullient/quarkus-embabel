package io.quarkiverse.embabel.it.agent;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration test for CalculatorAgent demonstrating @LlmTool usage.
 * <p>
 * This test verifies the complete @LlmTool integration flow:
 * <ul>
 * <li><b>Tool Discovery & Registration:</b> @LlmTool methods are discovered and registered.
 * WireMock mappings verify tools are included in LLM requests via JsonPath assertions
 * like {@code $.tools[?(@.function.name == 'add')]}.</li>
 * <li><b>Tool Specification Conversion:</b> Embabel @LlmTool annotations are converted
 * to LangChain4j tool format and sent to the LLM.</li>
 * <li><b>Tool Execution:</b> When LLM requests a tool call, the actual Java method
 * is invoked. Tests verify this by checking {@code toolsInvoked} in the response.</li>
 * <li><b>Result Flow:</b> Tool results are sent back to LLM, which generates final answer.
 * Tests verify correct answers (e.g., 5+3=8, 2^8=256).</li>
 * </ul>
 * <p>
 * WireMock simulates the two-step LLM interaction:
 * <ol>
 * <li>Initial request with tools → LLM responds with tool_calls</li>
 * <li>Request with tool results → LLM generates final answer</li>
 * </ol>
 */
@QuarkusTest
class CalculatorAgentIntegrationTest {

    @Test
    void shouldSolveMathProblemUsingTools() {
        // When: Request calculation that requires tool use
        // The LLM will call the 'add' tool to compute 5 + 3
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "problem": "What is 5 plus 3?"
                        }
                        """)
                .when()
                .post("/calculator/solve")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                // Verify the result structure
                .body("question", equalTo("What is 5 plus 3?"))
                .body("answer", equalTo(8.0f))
                .body("explanation", notNullValue())
                // Verify tool was actually invoked
                .body("toolsInvoked", notNullValue())
                .body("toolsInvoked.size()", equalTo(1))
                .body("toolsInvoked[0]", equalTo("add(5.0, 3.0)"));
    }

    @Test
    void shouldHandleMultiplication() {
        // When: Request multiplication that uses the multiply tool
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "problem": "Calculate 2 times 4"
                        }
                        """)
                .when()
                .post("/calculator/solve")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("question", equalTo("Calculate 2 times 4"))
                .body("answer", equalTo(8.0f))
                .body("explanation", notNullValue())
                // Verify multiply tool was invoked
                .body("toolsInvoked", notNullValue())
                .body("toolsInvoked.size()", equalTo(1))
                .body("toolsInvoked[0]", equalTo("multiply(2.0, 4.0)"));
    }

    @Test
    void shouldHandlePowerCalculation() {
        // When: Request power calculation
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "problem": "What is 2 to the power of 8?"
                        }
                        """)
                .when()
                .post("/calculator/solve")
                .then()
                .statusCode(200)
                .body("question", equalTo("What is 2 to the power of 8?"))
                .body("answer", equalTo(256.0f))
                .body("explanation", notNullValue())
                // Verify power tool was invoked
                .body("toolsInvoked", notNullValue())
                .body("toolsInvoked.size()", equalTo(1))
                .body("toolsInvoked[0]", equalTo("power(2.0, 8.0)"));
    }
}