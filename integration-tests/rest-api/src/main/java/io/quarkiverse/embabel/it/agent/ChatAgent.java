package io.quarkiverse.embabel.it.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.Ai;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Conversation;
import com.embabel.chat.UserMessage;

/**
 * Simple chat agent that responds to user messages in a conversation.
 * <p>
 * This agent demonstrates basic conversational AI capabilities:
 * <ul>
 * <li>Maintains conversation history</li>
 * <li>Responds to user messages using LLM</li>
 * <li>Uses the conversation context for coherent responses</li>
 * </ul>
 * <p>
 * The agent is triggered by {@link UserMessage} events and uses the
 * {@link Ai} builder to generate contextual responses via the LLM.
 */
@Agent(description = "Respond to user messages in a conversation")
public class ChatAgent {

    /**
     * Respond to user messages in the conversation.
     * <p>
     * This action is triggered when a {@link UserMessage} is added to the conversation.
     * It uses the LLM to generate a contextual response based on the conversation history.
     *
     * @param conversation the conversation containing the user message
     * @param ai the AI builder for LLM operations
     * @param context the action context for sending responses
     */
    @Action(canRerun = true, trigger = UserMessage.class)
    void respondToUser(Conversation conversation, Ai ai, ActionContext context) {
        // Get the last user message
        var lastMessage = conversation.getMessages().get(conversation.getMessages().size() - 1);

        // Generate a response using the LLM with conversation context
        String prompt = String.format(
                "You are a helpful assistant. Respond to the user's message: %s",
                lastMessage.getContent());

        // Use the LLM to generate a response
        String response = ai
                .withAutoLlm()
                .generateText(prompt);

        // Send the response back to the conversation
        var assistantMessage = new AssistantMessage(response);
        context.sendMessage(conversation.addMessage(assistantMessage));
    }
}