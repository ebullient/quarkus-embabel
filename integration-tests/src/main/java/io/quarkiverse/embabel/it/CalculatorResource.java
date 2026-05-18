package io.quarkiverse.embabel.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;

import io.quarkiverse.embabel.it.agent.CalculatorAgent.CalculationResult;

/**
 * REST endpoint demonstrating @LlmTool usage with Embabel agents.
 * <p>
 * This resource shows how agents can use @LlmTool methods that the LLM
 * can call during execution to perform specific tasks (in this case, math operations).
 */
@Path("/calculator")
public class CalculatorResource {

    @Inject
    AgentPlatform agentPlatform;

    /**
     * Request DTO for calculation.
     */
    public record CalculationRequest(String problem) {
    }

    /**
     * Solve a mathematical problem using the CalculatorAgent.
     * <p>
     * The agent will use its @LlmTool methods (add, multiply, power) to
     * perform calculations as directed by the LLM.
     *
     * @param request The mathematical problem to solve
     * @return Calculation result with explanation
     */
    @POST
    @Path("/solve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public CalculationResult solve(CalculationRequest request) {
        // Create invocation for the expected result type
        AgentInvocation<CalculationResult> invocation = AgentInvocation.create(
                agentPlatform,
                CalculationResult.class);

        // Create input object
        UserInput userInput = new UserInput(request.problem());

        // Invoke synchronously - framework will execute the agent's calculate() action
        // During execution, the LLM may call the @LlmTool methods (add, multiply, power)
        return invocation.invoke(userInput);
    }
}