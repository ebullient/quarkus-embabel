package io.quarkiverse.embabel.it.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.agent.test.unit.FakePromptRunner;

import io.quarkiverse.embabel.it.agent.WriteAndReviewAgent.Story;

/**
 * Unit test for WriteAndReviewAgent using FakeOperationContext.
 * <p>
 * Tests agent logic without requiring actual LLM calls, verifying:
 * - Individual action execution
 * - Prompt construction
 * - Multi-step workflow (craft → review)
 * - Record type handling
 */
class WriteAndReviewAgentTest {

    @Test
    void testCraftStory() {
        // Given
        var context = FakeOperationContext.create();
        var expectedStory = new Story("Once upon a time in a magical kingdom...");
        context.expectResponse(expectedStory);

        var agent = new WriteAndReviewAgent(100, 50);
        var userInput = new UserInput("Tell me a story about a magical kingdom", Instant.now());

        // When
        var story = agent.craftStory(userInput, context.ai());

        // Then
        assertNotNull(story, "Story should not be null");
        assertEquals(expectedStory.text(), story.text(), "Story text should match expected");

        // Verify the prompt
        var promptRunner = (FakePromptRunner) context.promptRunner();
        var invocations = promptRunner.getLlmInvocations();
        assertEquals(1, invocations.size(), "Should have one LLM invocation");

        var prompt = invocations.get(0).getMessages().get(0).getContent();
        assertTrue(prompt.contains("magical kingdom"), "Prompt should contain user input");
        assertTrue(prompt.contains("100 words"), "Prompt should specify story word count");
        assertTrue(prompt.contains("Craft a short story"), "Prompt should contain instruction");
    }

    @Test
    void testReviewStory() {
        // Given
        var context = FakeOperationContext.create();
        var story = new Story("A tale of adventure and mystery in ancient lands...");
        var expectedReview = "This story shows great imagination and engaging narrative.";
        context.expectResponse(expectedReview);

        var agent = new WriteAndReviewAgent(100, 50);
        var userInput = new UserInput("Write about adventure", Instant.now());

        // When
        var reviewedStory = agent.reviewStory(userInput, story, context.ai());

        // Then
        assertNotNull(reviewedStory, "ReviewedStory should not be null");
        assertEquals(story, reviewedStory.story(), "Should contain original story");
        assertEquals(expectedReview, reviewedStory.review(), "Review should match expected");
        assertNotNull(reviewedStory.reviewer(), "Reviewer should not be null");

        // Verify the review prompt
        var promptRunner = (FakePromptRunner) context.promptRunner();
        var invocations = promptRunner.getLlmInvocations();
        assertEquals(1, invocations.size(), "Should have one LLM invocation");

        var prompt = invocations.get(0).getMessages().get(0).getContent();
        assertTrue(prompt.contains("ancient lands"), "Prompt should contain story text");
        assertTrue(prompt.contains("adventure"), "Prompt should contain original user input");
        assertTrue(prompt.contains("50 words"), "Prompt should specify review word count");
    }

    @Test
    void testReviewedStoryContent() {
        // Given
        var story = new Story("Test story content");
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
        var story = new Story("Test");
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

        // Timestamp is created on-demand in getTimestamp(), so just verify it's reasonable
        assertTrue(reviewedStory.getTimestamp().isBefore(Instant.now().plusSeconds(1)),
                "Timestamp should be before now + 1 second");
    }

    @Test
    void testCustomWordCounts() {
        // Given
        var context = FakeOperationContext.create();
        context.expectResponse(new Story("Short story"));

        var agent = new WriteAndReviewAgent(50, 25);
        var userInput = new UserInput("Brief tale", Instant.now());

        // When
        agent.craftStory(userInput, context.ai());

        // Then - verify custom word count in prompt
        var promptRunner = (FakePromptRunner) context.promptRunner();
        var prompt = promptRunner.getLlmInvocations().get(0).getMessages().get(0).getContent();
        assertTrue(prompt.contains("50 words"), "Should use custom story word count");
    }

    @Test
    void testDefaultWordCounts() {
        // Given
        var context = FakeOperationContext.create();
        context.expectResponse(new Story("Default story"));

        var agent = new WriteAndReviewAgent(); // Using default constructor
        var userInput = new UserInput("Default", Instant.now());

        // When
        agent.craftStory(userInput, context.ai());

        // Then - verify default word count (100)
        var promptRunner = (FakePromptRunner) context.promptRunner();
        var prompt = promptRunner.getLlmInvocations().get(0).getMessages().get(0).getContent();
        assertTrue(prompt.contains("100 words"), "Should use default word count of 100");
    }
}
