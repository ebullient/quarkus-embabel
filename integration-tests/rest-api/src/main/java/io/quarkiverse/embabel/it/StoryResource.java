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
import io.quarkiverse.embabel.it.agent.WriteAndReviewAgent.ReviewedStory;

/**
 * REST endpoint demonstrating how to invoke Embabel agent @Action methods.
 * <p>
 * This resource shows two patterns:
 * <ol>
 * <li><b>Single-step invocation</b> - Request a Story, framework executes craftStory()</li>
 * <li><b>Multi-step invocation</b> - Request a ReviewedStory, framework chains craftStory() → reviewStory()</li>
 * </ol>
 * <p>
 * The framework uses GOAP (Goal-Oriented Action Planning) to automatically
 * find the sequence of actions needed to reach the requested goal type.
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
     * Synchronously craft a story using the WriteAndReviewAgent.
     * <p>
     * This demonstrates single-step invocation:
     * <ol>
     * <li>Find agent with goal matching Story type</li>
     * <li>Create agent process with UserInput on blackboard</li>
     * <li>Execute craftStory @Action method</li>
     * <li>Return the generated Story</li>
     * </ol>
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

    /**
     * Synchronously craft and review a story using the WriteAndReviewAgent.
     * <p>
     * This demonstrates multi-step invocation with action chaining:
     * <ol>
     * <li>Find agent with goal matching ReviewedStory type</li>
     * <li>Framework discovers reviewStory() needs Story as input</li>
     * <li>Framework executes craftStory() first to create Story</li>
     * <li>Framework executes reviewStory() with the Story</li>
     * <li>Return the ReviewedStory</li>
     * </ol>
     * <p>
     * The @AchievesGoal annotation on reviewStory() marks it as the
     * action that produces the final ReviewedStory goal.
     *
     * @param request The story prompt
     * @return Generated and reviewed story
     */
    @POST
    @Path("/review")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ReviewedStory craftAndReview(StoryRequest request) {
        // Create invocation for ReviewedStory - framework will chain actions
        AgentInvocation<ReviewedStory> invocation = AgentInvocation.create(agentPlatform, ReviewedStory.class);

        // Create input object
        UserInput userInput = new UserInput(request.prompt());

        // Invoke synchronously - framework chains craftStory() → reviewStory()
        return invocation.invoke(userInput);
    }
}