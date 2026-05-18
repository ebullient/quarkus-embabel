package io.quarkiverse.embabel.it.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.logging.Logger;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.common.ai.model.LlmOptions;

/**
 * Agent demonstrating @LlmTool usage in Quarkus integration tests.
 * <p>
 * This agent provides mathematical tools that the LLM can call during
 * execution to perform calculations. It demonstrates:
 * <ul>
 * <li>Tool discovery and registration</li>
 * <li>Tool specification conversion (Embabel → LangChain4j)</li>
 * <li>Tool execution when requested by LLM</li>
 * <li>Result conversion back to LLM</li>
 * </ul>
 */
@Agent(description = "Perform mathematical calculations using LLM-callable tools")
public class CalculatorAgent {

    private static final Logger LOG = Logger.getLogger(CalculatorAgent.class);

    private final List<String> toolInvocations = new ArrayList<>();

    /**
     * Result of a calculation with explanation.
     */
    public record CalculationResult(
            String question,
            double answer,
            String explanation,
            List<String> toolsInvoked) {
    }

    /**
     * Get the list of tool invocations (for testing/verification).
     */
    public List<String> getToolInvocations() {
        return Collections.unmodifiableList(toolInvocations);
    }

    /**
     * Clear tool invocation history (for testing).
     */
    public void clearToolInvocations() {
        toolInvocations.clear();
    }

    /**
     * Add two numbers together.
     * <p>
     * This tool can be called by the LLM during agent execution.
     *
     * @param a First number
     * @param b Second number
     * @return Sum of a and b
     */
    @LlmTool(description = "Add two numbers together")
    public double add(double a, double b) {
        String invocation = String.format("add(%.1f, %.1f)", a, b);
        toolInvocations.add(invocation);
        LOG.infof("Tool invoked: %s", invocation);
        return a + b;
    }

    /**
     * Multiply two numbers together.
     * <p>
     * This tool can be called by the LLM during agent execution.
     *
     * @param a First number
     * @param b Second number
     * @return Product of a and b
     */
    @LlmTool(description = "Multiply two numbers together")
    public double multiply(double a, double b) {
        String invocation = String.format("multiply(%.1f, %.1f)", a, b);
        toolInvocations.add(invocation);
        LOG.infof("Tool invoked: %s", invocation);
        return a * b;
    }

    /**
     * Calculate the power of a number.
     * <p>
     * This tool can be called by the LLM during agent execution.
     *
     * @param base The base number
     * @param exponent The exponent
     * @return base raised to the power of exponent
     */
    @LlmTool(description = "Calculate the power of a number (base^exponent)")
    public double power(double base, double exponent) {
        String invocation = String.format("power(%.1f, %.1f)", base, exponent);
        toolInvocations.add(invocation);
        LOG.infof("Tool invoked: %s", invocation);
        return Math.pow(base, exponent);
    }

    /**
     * Solve a mathematical problem using available tools.
     * <p>
     * The LLM will analyze the user's question and call the appropriate
     * tools (add, multiply, power) to compute the answer.
     *
     * @param userInput The mathematical question
     * @param ai AI builder for LLM operations
     * @return Calculation result with explanation
     */
    @AchievesGoal(description = "Mathematical problem has been solved")
    @Action
    public CalculationResult calculate(UserInput userInput, Ai ai) {
        // Clear previous invocations for this calculation
        toolInvocations.clear();

        // The @LlmTool methods (add, multiply, power) are automatically discovered at deployment time
        // via Tool.safelyFromInstance() in QuarkusAgentDeployer and bound to this action.
        // Make them available to the LLM via withToolObject().
        String response = ai
                .withLlm(LlmOptions
                        .withAutoLlm()
                        .withTemperature(0.0)) // Use deterministic responses for math
                .withToolObject(this) // Provide this agent's @LlmTool methods to the LLM
                .generateText(String.format("""
                        You are a helpful math assistant with access to calculation tools.
                        Solve the following mathematical problem step by step.
                        Use the available tools (add, multiply, power) to perform calculations.
                        Explain your reasoning clearly.

                        Problem: %s

                        Provide your answer in this format:
                        Answer: [the numerical result]
                        Explanation: [step-by-step explanation of how you solved it]
                        """,
                        userInput.getContent()).trim());

        // Parse the response to extract answer and explanation
        double answer = parseAnswer(response);
        String explanation = parseExplanation(response);

        LOG.infof("Calculation completed. Tools invoked: %s", toolInvocations);

        return new CalculationResult(
                userInput.getContent(),
                answer,
                explanation,
                new ArrayList<>(toolInvocations));
    }

    /**
     * Parse the numerical answer from the LLM response.
     */
    private double parseAnswer(String response) {
        // Look for "Answer: <number>" pattern
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith("Answer:")) {
                String numberStr = line.substring(line.indexOf(":") + 1).trim();
                // Remove any non-numeric characters except decimal point and minus
                numberStr = numberStr.replaceAll("[^0-9.\\-]", "");
                try {
                    return Double.parseDouble(numberStr);
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            }
        }
        return 0.0;
    }

    /**
     * Parse the explanation from the LLM response.
     */
    private String parseExplanation(String response) {
        // Look for "Explanation:" pattern
        int explanationIndex = response.indexOf("Explanation:");
        if (explanationIndex >= 0) {
            return response.substring(explanationIndex + "Explanation:".length()).trim();
        }
        return response;
    }
}