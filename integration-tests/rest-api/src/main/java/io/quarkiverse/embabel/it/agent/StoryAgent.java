package io.quarkiverse.embabel.it.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import com.embabel.common.ai.model.LlmOptions;

/**
 * Simple story-writing agent for integration testing.
 * Demonstrates basic Embabel Agent Framework usage in Quarkus.
 */
@Agent(description = "Generate short stories based on user input")
public class StoryAgent {

    private static final RoleGoalBackstory WRITER = new RoleGoalBackstory(
            "Creative Writer",
            "Write engaging short stories",
            "Experienced storyteller with a vivid imagination");

    private final int wordCount;

    public StoryAgent() {
        this(100); // Default to 100 words
    }

    StoryAgent(int wordCount) {
        this.wordCount = wordCount;
    }

    /**
     * Craft a short story based on user input.
     *
     * @param userInput The user's story prompt
     * @param ai The AI builder for LLM operations
     * @return A generated story
     */
    @AchievesGoal(description = "Story has been crafted")
    @Action
    public Story craftStory(UserInput userInput, Ai ai) {
        String text = ai
                .withLlm(LlmOptions
                        .withAutoLlm()
                        .withTemperature(0.7))
                .withPromptContributor(WRITER)
                .generateText(String.format("""
                        Craft a short story in %d words or less.
                        The story should be engaging and imaginative.
                        Use the user's input as inspiration.

                        # User input
                        %s
                        """,
                        wordCount,
                        userInput.getContent()).trim());

        return new Story(text);
    }
}