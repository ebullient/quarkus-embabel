package com.embabel.template.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.agent.test.unit.FakePromptRunner;

/**
 * Unit test for WriteAndReviewAgent using FakeOperationContext.
 * <p>
 * Tests the complete workflow of writing and reviewing a story without requiring actual LLM calls.
 * This verifies:
 * - Individual action execution
 * - Prompt construction
 * - Multi-step workflow (craft → review)
 * - Record type handling
 */
class WriteAndReviewAgentIntegrationTest {

    @BeforeAll
    static void setUp() {
        // Set shell configuration to non-interactive mode
        System.setProperty("embabel.agent.shell.interactive.enabled", "false");
    }

    @Test
    void shouldExecuteCompleteWorkflow() {
        // Given - Set up fake context with expected responses
        var context = FakeOperationContext.create();

        var story = new WriteAndReviewAgent.Story("AI will transform our world...");
        var reviewText = "Excellent exploration of AI themes.";

        // First call will be craftStory (creating Story object)
        context.expectResponse(story);

        // Second call will be reviewStory (generating review text)
        context.expectResponse(reviewText);

        var agent = new WriteAndReviewAgent(100, 100);
        var userInput = new UserInput("Write about artificial intelligence", Instant.now());

        // When - Execute craftStory
        var craftedStory = agent.craftStory(userInput, context.ai());

        // Then - Verify story was crafted
        assertNotNull(craftedStory, "Story should not be null");
        assertEquals(story.text(), craftedStory.text(), "Story text should match expected");

        // When - Execute reviewStory
        var reviewedStory = agent.reviewStory(userInput, craftedStory, context.ai());

        // Then - Verify review was created
        assertNotNull(reviewedStory, "ReviewedStory should not be null");
        assertEquals(craftedStory, reviewedStory.story(), "Should contain the crafted story");
        assertEquals(reviewText, reviewedStory.review(), "Review should match expected");
        assertEquals(Personas.REVIEWER, reviewedStory.reviewer(), "Reviewer should match expected persona");

        // Verify the content includes both story and review
        var content = reviewedStory.getContent();
        assertTrue(content.contains(story.text()),
                "Expected story content to be present: " + content);
        assertTrue(content.contains(reviewText),
                "Expected review to be present: " + content);

        // Verify prompts were constructed correctly
        var promptRunner = (FakePromptRunner) context.promptRunner();
        var invocations = promptRunner.getLlmInvocations();
        assertEquals(2, invocations.size(), "Should have two LLM invocations (craft + review)");

        // Verify craft story prompt
        var craftPrompt = invocations.get(0).getMessages().get(0).getContent();
        assertTrue(craftPrompt.contains("Craft a short story"),
                "Craft prompt should contain instruction");
        assertTrue(craftPrompt.contains("artificial intelligence"),
                "Craft prompt should contain user input");

        // Verify review story prompt
        var reviewPrompt = invocations.get(1).getMessages().get(0).getContent();
        assertTrue(reviewPrompt.contains("You will be given a short story to review"),
                "Review prompt should contain instruction");
        assertTrue(reviewPrompt.contains(story.text()),
                "Review prompt should contain the story text");
    }

    @Test
    void testCraftStoryWithHighTemperature() {
        // Given
        var context = FakeOperationContext.create();
        var expectedStory = new WriteAndReviewAgent.Story("Once upon a time in a magical kingdom...");
        context.expectResponse(expectedStory);

        var agent = new WriteAndReviewAgent(100, 50);
        var userInput = new UserInput("Tell me a story about a magical kingdom", Instant.now());

        // When
        var story = agent.craftStory(userInput, context.ai());

        // Then
        assertNotNull(story, "Story should not be null");
        assertEquals(expectedStory.text(), story.text(), "Story text should match expected");

        // Verify the LLM was configured with higher temperature for creativity
        var promptRunner = (FakePromptRunner) context.promptRunner();
        var invocations = promptRunner.getLlmInvocations();
        assertEquals(1, invocations.size(), "Should have one LLM invocation");

        // Note: Temperature verification would require access to LlmOptions in FakePromptRunner
        // For now, we verify the prompt content
        var prompt = invocations.get(0).getMessages().get(0).getContent();
        assertTrue(prompt.contains("magical kingdom"), "Prompt should contain user input");
        assertTrue(prompt.contains("100 words"), "Prompt should specify story word count");
    }

    @Test
    void testReviewedStoryContent() {
        // Given
        var story = new WriteAndReviewAgent.Story("Test story content");
        var review = "Great narrative structure.";
        var context = FakeOperationContext.create();
        context.expectResponse(review);

        var agent = new WriteAndReviewAgent();
        var userInput = new UserInput("Test", Instant.now());

        // When
        var reviewedStory = agent.reviewStory(userInput, story, context.ai());

        // Then - verify HasContent implementation
        var content = reviewedStory.getContent();
        assertTrue(content.contains("# Story"), "Content should have Story header");
        assertTrue(content.contains("Test story content"), "Content should include story text");
        assertTrue(content.contains("# Review"), "Content should have Review header");
        assertTrue(content.contains("Great narrative structure"), "Content should include review");
        assertTrue(content.contains("# Reviewer"), "Content should have Reviewer header");
    }

    @Test
    void testReviewedStoryTimestamp() {
        // Given
        var story = new WriteAndReviewAgent.Story("Test");
        var context = FakeOperationContext.create();
        context.expectResponse("Review");

        var agent = new WriteAndReviewAgent();
        var userInput = new UserInput("Test", Instant.now());

        // When
        var before = Instant.now();
        var reviewedStory = agent.reviewStory(userInput, story, context.ai());

        // Then - verify Timestamped implementation
        assertNotNull(reviewedStory.getTimestamp(), "Timestamp should not be null");
        assertTrue(!reviewedStory.getTimestamp().isBefore(before),
                "Timestamp should be after or equal to test start");
        assertTrue(reviewedStory.getTimestamp().isBefore(Instant.now().plusSeconds(1)),
                "Timestamp should be before now + 1 second");
    }
}
