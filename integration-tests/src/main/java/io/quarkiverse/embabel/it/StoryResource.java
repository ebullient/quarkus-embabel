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

import io.quarkiverse.embabel.it.agent.Story;
import io.quarkiverse.embabel.it.agent.StoryAgent;

/**
 * REST endpoint demonstrating how to invoke Embabel agent @Action methods.
 *
 * This resource shows the pattern for triggering agent execution from HTTP requests:
 * 1. Inject AgentPlatform (contains registered agents)
 * 2. Create AgentInvocation with expected result type
 * 3. Call invoke() with input - framework discovers agent and executes @Action methods
 */
@Path("/story")
public class StoryResource {

    @Inject
    AgentPlatform agentPlatform;

    /**
     * Request DTO for story generation.
     */
    public record StoryRequest(String prompt) {
    }

    /**
     * Synchronously craft a story using the StoryAgent.
     *
     * The framework will:
     * 1. Find the agent with a goal matching Story type
     * 2. Create an agent process with UserInput on the blackboard
     * 3. Use GOAP planner to find path from input to goal
     * 4. Execute the craftStory @Action method
     * 5. Return the generated Story
     *
     * @param request The story prompt
     * @return Generated story
     */
    @POST
    @Path("/craft")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Story craftStory(StoryRequest request) {
        // Create invocation for the expected result type
        AgentInvocation<Story> invocation = AgentInvocation.create(agentPlatform, Story.class);

        // Create input object
        UserInput userInput = new UserInput(request.prompt());

        // Invoke synchronously - framework finds agent with matching goal
        // and executes the @Action method(s) to reach that goal
        return invocation.invoke(userInput);
    }
}