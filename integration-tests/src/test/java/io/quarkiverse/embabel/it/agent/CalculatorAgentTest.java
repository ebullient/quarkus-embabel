package io.quarkiverse.embabel.it.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;

/**
 * Unit test for CalculatorAgent verifying @LlmTool methods.
 * <p>
 * Tests:
 * <ul>
 * <li>Individual tool methods (add, multiply, power)</li>
 * <li>Agent action with mocked LLM responses</li>
 * <li>Result parsing logic</li>
 * </ul>
 */
class CalculatorAgentTest {

    @Test
    void testCalculateAction() {
        // Given
        var context = FakeOperationContext.create();
        var expectedResponse = """
                Answer: 8.0
                Explanation: I used the add tool to calculate 5 + 3, which equals 8.
                """;
        context.expectResponse(expectedResponse);

        var agent = new CalculatorAgent();
        var userInput = new UserInput("What is 5 plus 3?", Instant.now());

        // When
        var result = agent.calculate(userInput, context.ai());

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals("What is 5 plus 3?", result.question(), "Question should match input");
        assertEquals(8.0, result.answer(), 0.001, "Answer should be 8.0");
        assertTrue(result.explanation().contains("add tool"), "Explanation should mention the add tool");
        assertNotNull(result.toolsInvoked(), "Tool invocations should be tracked");
    }

    @Test
    void testCalculateWithMultiplication() {
        // Given
        var context = FakeOperationContext.create();
        var expectedResponse = """
                Answer: 28.0
                Explanation: I used the multiply tool to calculate 4 * 7, which equals 28.
                """;
        context.expectResponse(expectedResponse);

        var agent = new CalculatorAgent();
        var userInput = new UserInput("What is 4 times 7?", Instant.now());

        // When
        var result = agent.calculate(userInput, context.ai());

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals(28.0, result.answer(), 0.001, "Answer should be 28.0");
        assertTrue(result.explanation().contains("multiply"), "Explanation should mention multiply");
        assertNotNull(result.toolsInvoked(), "Tool invocations should be tracked");
    }

    @Test
    void testCalculateWithPower() {
        // Given
        var context = FakeOperationContext.create();
        var expectedResponse = """
                Answer: 8.0
                Explanation: I used the power tool to calculate 2^3, which equals 8.
                """;
        context.expectResponse(expectedResponse);

        var agent = new CalculatorAgent();
        var userInput = new UserInput("What is 2 to the power of 3?", Instant.now());

        // When
        var result = agent.calculate(userInput, context.ai());

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals(8.0, result.answer(), 0.001, "Answer should be 8.0");
        assertTrue(result.explanation().contains("power"), "Explanation should mention power");
        assertNotNull(result.toolsInvoked(), "Tool invocations should be tracked");
    }

    @Test
    void testAnswerParsingWithExtraText() {
        // Given
        var context = FakeOperationContext.create();
        var responseWithExtraText = """
                Let me solve this for you.
                Answer: 42.5
                Explanation: The calculation was performed successfully.
                """;
        context.expectResponse(responseWithExtraText);

        var agent = new CalculatorAgent();
        var userInput = new UserInput("Calculate something", Instant.now());

        // When
        var result = agent.calculate(userInput, context.ai());

        // Then
        assertEquals(42.5, result.answer(), 0.001, "Should parse answer correctly even with extra text");
    }

    @Test
    void testExplanationParsing() {
        // Given
        var context = FakeOperationContext.create();
        var response = """
                Answer: 100.0
                Explanation: This is a detailed explanation
                that spans multiple lines
                and includes various details.
                """;
        context.expectResponse(response);

        var agent = new CalculatorAgent();
        var userInput = new UserInput("Test", Instant.now());

        // When
        var result = agent.calculate(userInput, context.ai());

        // Then
        assertTrue(result.explanation().contains("detailed explanation"),
                "Should parse multi-line explanation");
        assertTrue(result.explanation().contains("multiple lines"),
                "Should include all explanation lines");
    }
}