package io.quarkiverse.embabel.it;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.embabel.chat.Conversation;
import com.embabel.chat.ConversationFactory;
import com.embabel.chat.UserMessage;

/**
 * REST endpoint demonstrating simple chat interactions.
 * <p>
 * This resource shows how to:
 * <ul>
 * <li>Create and manage conversation sessions</li>
 * <li>Send user messages to a conversation</li>
 * <li>Maintain conversation history across multiple turns</li>
 * </ul>
 * <p>
 * This is a simplified chat endpoint for integration testing.
 * In a real application, you would integrate with ChatAgent for LLM responses.
 */
@Path("/chat")
public class ChatResource {

    @Inject
    ConversationFactory conversationFactory;

    // Simple in-memory session storage for demo purposes
    private final Map<String, Conversation> sessions = new ConcurrentHashMap<>();

    /**
     * Request DTO for chat messages.
     */
    public record ChatRequest(String message) {
    }

    /**
     * Response DTO for chat messages.
     */
    public record ChatResponse(String sessionId, String message, int messageCount) {
    }

    /**
     * Start a new chat session.
     * <p>
     * Creates a new conversation for the session.
     *
     * @return The session ID and initial message count
     */
    @POST
    @Path("/session")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> createSession() {
        // Generate a unique session ID
        String sessionId = UUID.randomUUID().toString();

        // Create a new conversation
        Conversation conversation = conversationFactory.create(sessionId);

        // Store the conversation
        sessions.put(sessionId, conversation);

        return Map.of(
                "sessionId", sessionId,
                "messageCount", 0);
    }

    /**
     * Send a message to an existing chat session.
     * <p>
     * This demonstrates basic conversation management:
     * <ol>
     * <li>Add user message to conversation</li>
     * <li>Return echo response for testing</li>
     * </ol>
     *
     * @param sessionId The session ID
     * @param request The user's message
     * @return Echo response
     */
    @POST
    @Path("/session/{sessionId}/message")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ChatResponse sendMessage(@PathParam("sessionId") String sessionId, ChatRequest request) {
        Conversation conversation = sessions.get(sessionId);
        if (conversation == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        // Add user message to conversation
        UserMessage userMessage = new UserMessage(request.message());
        conversation.addMessage(userMessage);

        // For this simple demo, echo back the message
        String response = "Echo: " + request.message();

        return new ChatResponse(
                sessionId,
                response,
                conversation.getMessages().size());
    }

    /**
     * Get the conversation history for a session.
     *
     * @param sessionId The session ID
     * @return The message count and messages
     */
    @GET
    @Path("/session/{sessionId}/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getHistory(@PathParam("sessionId") String sessionId) {
        Conversation conversation = sessions.get(sessionId);
        if (conversation == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        var messages = conversation.getMessages();
        return Map.of(
                "sessionId", sessionId,
                "messageCount", messages.size(),
                "messages", messages.stream()
                        .map(m -> Map.of(
                                "role", m.getClass().getSimpleName(),
                                "content", m.getContent()))
                        .toList());
    }
}