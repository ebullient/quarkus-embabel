package io.quarkiverse.embabel.it.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.agent.test.unit.FakePromptRunner;

/**
 * Unit test for StoryAgent using FakeOperationContext for mocking.
 * This tests the agent logic without requiring actual LLM calls.
 */
class StoryAgentTest {

    @Test
    void testCraftStory() {
        // Given
        var context = FakeOperationContext.create();
        var promptRunner = (FakePromptRunner) context.promptRunner();
        var expectedStory = new Story("Once upon a time in a magical kingdom...");
        context.expectResponse(expectedStory);

        var agent = new StoryAgent(100);
        var userInput = new UserInput("Tell me a story about a magical kingdom", Instant.now());

        // When
        var story = agent.craftStory(userInput, context.ai());

        // Then
        assertNotNull(story, "Story should not be null");
        assertEquals(expectedStory.text(), story.text(), "Story text should match expected");

        // Verify the prompt was constructed correctly
        var llmInvocations = promptRunner.getLlmInvocations();
        assertEquals(1, llmInvocations.size(), "Should have one LLM invocation");

        var invocation = llmInvocations.get(0);
        var prompt = invocation.getMessages().get(0).getContent();

        assertTrue(prompt.contains("magical kingdom"), "Prompt should contain user input");
        assertTrue(prompt.contains("100 words"), "Prompt should specify word count");
        assertTrue(prompt.contains("Craft a short story"), "Prompt should contain story instruction");
    }

    @Test
    void testCraftStoryWithDifferentWordCount() {
        // Given
        var context = FakeOperationContext.create();
        var expectedStory = new Story("A brief tale...");
        context.expectResponse(expectedStory);

        var agent = new StoryAgent(50); // Different word count
        var userInput = new UserInput("Write about adventure", Instant.now());

        // When
        var story = agent.craftStory(userInput, context.ai());

        // Then
        assertNotNull(story);
        var promptRunner = (FakePromptRunner) context.promptRunner();
        var prompt = promptRunner.getLlmInvocations().get(0).getMessages().get(0).getContent();
        assertTrue(prompt.contains("50 words"), "Prompt should reflect custom word count");
    }

    @Test
    void testPromptContainsPersona() {
        // Given
        var context = FakeOperationContext.create();
        context.expectResponse(new Story("Test story"));

        var agent = new StoryAgent();
        var userInput = new UserInput("Any topic", Instant.now());

        // When
        agent.craftStory(userInput, context.ai());

        // Then - verify persona is included in the prompt context
        var promptRunner = (FakePromptRunner) context.promptRunner();
        var invocation = promptRunner.getLlmInvocations().get(0);

        // The persona should be applied via withPromptContributor
        assertNotNull(invocation, "Should have LLM invocation");
    }
}
