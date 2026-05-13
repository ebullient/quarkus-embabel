package io.quarkiverse.embabel.it.agent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.domain.library.HasContent;
import com.embabel.agent.prompt.persona.Persona;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.core.types.Timestamped;

/**
 * Multi-goal agent demonstrating chained actions in Quarkus.
 * <p>
 * This agent has two actions that work together:
 * <ol>
 * <li>craftStory - generates a Story based on user input</li>
 * <li>reviewStory - takes the Story and creates a ReviewedStory with feedback</li>
 * </ol>
 * <p>
 * The framework automatically chains these actions when the goal is ReviewedStory:
 * UserInput → craftStory() → Story → reviewStory() → ReviewedStory
 */
@Agent(description = "Generate a story based on user input and review it")
public class WriteAndReviewAgent {

    private static final RoleGoalBackstory WRITER = new RoleGoalBackstory(
            "Creative Storyteller",
            "Write engaging and imaginative stories",
            "Has a PhD in French literature; used to work in a circus");

    private static final Persona REVIEWER = new Persona(
            "Media Book Review",
            "New York Times Book Reviewer",
            "Professional and insightful",
            "Help guide readers toward good stories");

    /**
     * Represents a generated story.
     */
    public record Story(String text) {
    }

    /**
     * Represents a story with review feedback.
     * <p>
     * Implements HasContent and Timestamped to provide rich context
     * for agents and external systems.
     */
    public record ReviewedStory(
            Story story,
            String review,
            Persona reviewer) implements HasContent, Timestamped {

        @Override
        public Instant getTimestamp() {
            return Instant.now();
        }

        @Override
        public String getContent() {
            return String.format("""
                    # Story
                    %s

                    # Review
                    %s

                    # Reviewer
                    %s, %s
                    """,
                    story.text(),
                    review,
                    reviewer.getName(),
                    getTimestamp().atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")))
                    .trim();
        }
    }

    private final int storyWordCount;
    private final int reviewWordCount;

    /**
     * Creates a WriteAndReviewAgent with default word counts.
     */
    public WriteAndReviewAgent() {
        this(100, 100);
    }

    /**
     * Creates a WriteAndReviewAgent with custom word counts.
     *
     * @param storyWordCount Maximum words for generated stories
     * @param reviewWordCount Maximum words for reviews
     */
    WriteAndReviewAgent(int storyWordCount, int reviewWordCount) {
        this.storyWordCount = storyWordCount;
        this.reviewWordCount = reviewWordCount;
    }

    /**
     * Reviews a story and produces a ReviewedStory.
     * <p>
     * This action achieves the ReviewedStory goal. When the framework
     * needs a ReviewedStory, it will:
     * <ol>
     * <li>First execute craftStory() to get the Story</li>
     * <li>Then execute reviewStory() to create the ReviewedStory</li>
     * </ol>
     * <p>
     * The @AchievesGoal annotation marks this as exportable for remote invocation.
     *
     * @param userInput The original user input (for context in review)
     * @param story The story to review (provided by craftStory action)
     * @param ai AI builder for LLM operations
     * @return A reviewed story with feedback
     */
    @AchievesGoal(description = "The story has been crafted and reviewed by a book reviewer", export = @Export(remote = true, name = "writeAndReviewStory"))
    @Action
    public ReviewedStory reviewStory(UserInput userInput, Story story, Ai ai) {
        var review = ai
                .withAutoLlm()
                .withPromptContributor(REVIEWER)
                .generateText(String.format("""
                        You will be given a short story to review.
                        Review it in %d words or less.
                        Consider whether or not the story is engaging, imaginative, and well-written.
                        Also consider whether the story is appropriate given the original user input.

                        # Story
                        %s

                        # User input that inspired the story
                        %s
                        """,
                        reviewWordCount,
                        story.text(),
                        userInput.getContent()).trim());

        return new ReviewedStory(story, review, REVIEWER);
    }

    /**
     * Crafts a short story based on user input.
     * <p>
     * This action produces a Story, which can be used as input
     * to the reviewStory action.
     *
     * @param userInput The user's story prompt
     * @param ai AI builder for LLM operations
     * @return A generated story
     */
    @Action
    public Story craftStory(UserInput userInput, Ai ai) {
        return ai
                // Higher temperature for more creative output
                .withLlm(LlmOptions
                        .withAutoLlm() // You can also choose a specific model or role here
                        .withTemperature(.7))
                .withPromptContributor(WRITER)
                .creating(Story.class)
                .fromPrompt(String.format("""
                        Craft a short story in %d words or less.
                        The story should be engaging and imaginative.
                        Use the user's input as inspiration if possible.
                        If the user has provided a name, include it in the story.

                        # User input
                        %s
                        """,
                        storyWordCount,
                        userInput.getContent()).trim());
    }
}
