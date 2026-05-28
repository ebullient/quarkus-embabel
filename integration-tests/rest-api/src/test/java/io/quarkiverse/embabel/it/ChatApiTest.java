package io.quarkiverse.embabel.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.embabel.agent.api.channel.DevNullOutputChannel;
import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.channel.OutputChannelEvent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.ChatSession;
import com.embabel.chat.Chatbot;
import com.embabel.chat.ConversationFactory;
import com.embabel.chat.Message;
import com.embabel.chat.UserMessage;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test for Embabel Chat API support in quarkus-embabel.
 * <p>
 * This test verifies the Chat API integration as defined in the CAT (Chat API Test) checklist:
 * <ul>
 * <li><b>CAT-1:</b> Chatbot creation via {@code AgentProcessChatbot.utilityFromPlatform()}</li>
 * <li><b>CAT-2:</b> Session creation and restoration</li>
 * </ul>
 * <p>
 * The test uses CDI injection to verify that:
 * <ul>
 * <li>{@link Chatbot} is available as an injectable bean</li>
 * <li>{@link ConversationFactory} is available with default in-memory implementation</li>
 * <li>{@link AgentPlatform} is properly wired for session management</li>
 * </ul>
 *
 * @see com.embabel.chat.agent.AgentProcessChatbot
 * @see com.embabel.chat.support.InMemoryConversationFactory
 */
@QuarkusTest
class ChatApiTest {

    @Inject
    Chatbot chatbot;

    @Inject
    ConversationFactory conversationFactory;

    @Inject
    AgentPlatform agentPlatform;

    /**
     * Chatbot creation
     * <p>
     * Verifies that a {@link Chatbot} can be created and injected via CDI.
     * The chatbot should be backed by {@code AgentProcessChatbot.utilityFromPlatform()}
     * with the default in-memory conversation factory.
     */
    @Test
    void testChatbotCreation() {
        // Then: Chatbot is injected and available
        assertNotNull(chatbot, "Chatbot should be injectable via CDI");
        assertNotNull(conversationFactory, "ConversationFactory should be injectable via CDI");
        assertNotNull(agentPlatform, "AgentPlatform should be injectable via CDI");
    }

    /**
     * Session creation
     * <p>
     * Verifies that a {@link ChatSession} can be created using {@code createSession()}.
     * The session should have:
     * <ul>
     * <li>A non-null conversation</li>
     * <li>A valid process ID</li>
     * <li>An initialized conversation with the correct ID</li>
     * </ul>
     */
    @Test
    void testSessionCreation() {
        // When: Create a new chat session
        ChatSession session = chatbot.createSession(
                null, // user
                DevNullOutputChannel.INSTANCE, // outputChannel
                null, // contextId
                null, // conversationId (will be auto-generated)
                null // budget
        );

        // Then: Session is created successfully
        assertNotNull(session, "Session should not be null");
        assertNotNull(session.getConversation(), "Conversation should be initialized");
        assertNotNull(session.getProcessId(), "Process ID should be set");
        assertNotNull(session.getConversation().getId(), "Conversation ID should be set");
    }

    /**
     * Session creation with explicit conversation ID
     * <p>
     * Verifies that a session can be created with a specific conversation ID,
     * and that the conversation uses the provided ID rather than an auto-generated one.
     */
    @Test
    void testSessionCreationWithConversationId() {
        // Given: A specific conversation ID
        String conversationId = "test-conversation-123";

        // When: Create session with explicit conversation ID
        ChatSession session = chatbot.createSession(
                null, // user
                DevNullOutputChannel.INSTANCE, // outputChannel
                null, // contextId
                conversationId, // conversationId
                null // budget
        );

        // Then: Session uses the provided conversation ID
        assertNotNull(session, "Session should not be null");
        assertEquals(conversationId, session.getConversation().getId(),
                "Conversation should use the provided ID");
    }

    /**
     * Session restoration via findSession
     * <p>
     * Verifies that {@code findSession()} can retrieve active sessions.
     * <p>
     * <b>Current Behavior (Limitation)</b>: The implementation currently requires using
     * the process ID (auto-generated UUID) rather than the conversation ID to find sessions.
     * This is because {@link com.embabel.agent.core.AgentPlatform#createAgentProcess} generates
     * a random process ID via {@link com.embabel.agent.spi.AgentProcessIdGenerator}, and there's
     * no standard mechanism to override it with the conversation ID.
     * <p>
     * <b>Expected Behavior (from docs)</b>: {@code findSession(conversationId)} should work,
     * implying that when a conversation ID is provided, it should become the AgentProcess ID.
     * <p>
     * <b>Workaround</b>: Applications must track the mapping from conversation ID to process ID
     * if they want to restore sessions by conversation ID.
     * <ul>
     * <li>Currently works: {@code findSession(session.getProcessId())}</li>
     * <li>Currently fails: {@code findSession(conversationId)}</li>
     * </ul>
     *
     * @see <a href="https://github.com/embabel/embabel-agent/issues/XXX">Related Issue</a>
     */
    @Test
    void testSessionRestoration() {
        // Given: Create a session with explicit conversation ID
        String conversationId = "test-conversation-456";
        ChatSession created = chatbot.createSession(
                null, // user
                DevNullOutputChannel.INSTANCE, // outputChannel
                null, // contextId
                conversationId, // conversationId
                null // budget
        );
        assertNotNull(created, "Created session should not be null");
        assertEquals(conversationId, created.getConversation().getId(),
                "Conversation should have the requested ID");

        // Capture the auto-generated process ID
        String processId = created.getProcessId();
        assertNotNull(processId, "Process ID should be set");
        // Note: processId will be a UUID, NOT the conversation ID

        // Test 1: Try to find by conversation ID (documented behavior - currently doesn't work)
        ChatSession foundByConversationId = chatbot.findSession(conversationId);
        // KNOWN LIMITATION: This returns null because AgentProcess ID != conversation ID
        assertNull(foundByConversationId,
                "findSession(conversationId) currently doesn't work - known limitation");

        // Test 2: Find by process ID (current workaround)
        ChatSession foundByProcessId = chatbot.findSession(processId);
        assertNotNull(foundByProcessId,
                "findSession(processId) should work as workaround");
        assertEquals(processId, foundByProcessId.getProcessId(),
                "Found session should reference the same process");
        assertEquals(conversationId, foundByProcessId.getConversation().getId(),
                "Found session should have the same conversation");

        // Test 3: Non-existent session returns null
        ChatSession notFound = chatbot.findSession("does-not-exist-789");
        assertNull(notFound, "findSession should return null for non-existent process ID");
    }

    /**
     * User messages recorded
     * <p>
     * Verifies that when {@code onUserMessage()} is called:
     * <ul>
     * <li>The user message is added to the conversation history</li>
     * <li>The agent process is triggered and executes</li>
     * <li>Assistant responses are sent to the output channel</li>
     * </ul>
     */
    @Test
    void testUserMessageRecording() throws InterruptedException {
        // Given: Create a session with a queueing output channel to capture responses
        BlockingQueue<Message> responseQueue = new ArrayBlockingQueue<>(10);
        OutputChannel outputChannel = new QueueingOutputChannel(responseQueue);
        ChatSession session = chatbot.createSession(null, outputChannel, null, null, null);

        // When: Send a user message
        String userMessageText = "Hello, test agent!";
        UserMessage userMessage = new UserMessage(userMessageText);
        session.onUserMessage(userMessage);

        // Then: User message is in conversation history
        var conversation = session.getConversation();
        assertFalse(conversation.getMessages().isEmpty(), "Conversation should have messages");
        assertTrue(conversation.getMessages().stream()
                .anyMatch(m -> m instanceof UserMessage && m.getContent().equals(userMessageText)),
                "Conversation should contain the user message");

        // And: Agent process was triggered and sent a response
        Message response = responseQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(response, "Agent should have sent a response");
    }

    /**
     * Trigger events don't pollute conversation history
     * <p>
     * Verifies that when an action is triggered by an event (like {@code UserMessage}),
     * the trigger mechanism doesn't add duplicate or extra messages to the conversation.
     * Only messages explicitly added via {@code conversation.addMessage()} should appear.
     * <p>
     * This test checks that:
     * <ul>
     * <li>The conversation contains exactly the user message and assistant response</li>
     * <li>No trigger-related messages are added to the history</li>
     * <li>Message count matches expected (1 user + 1 assistant = 2 total)</li>
     * </ul>
     */
    @Test
    void testTriggerEventsDoNotPolluteHistory() throws InterruptedException {
        // Given: Create a session with a queueing output channel
        BlockingQueue<Message> responseQueue = new ArrayBlockingQueue<>(10);
        OutputChannel outputChannel = new QueueingOutputChannel(responseQueue);
        ChatSession session = chatbot.createSession(null, outputChannel, null, null, null);

        // When: Send a user message (which triggers the ChatAgent.respondToUser action)
        String userMessageText = "Test trigger cleanliness";
        UserMessage userMessage = new UserMessage(userMessageText);
        session.onUserMessage(userMessage);

        // Wait for agent to respond
        Message response = responseQueue.poll(10, TimeUnit.SECONDS);
        assertNotNull(response, "Agent should have sent a response");

        // Then: Conversation should contain exactly 2 messages (user + assistant)
        var conversation = session.getConversation();
        var messages = conversation.getMessages();
        assertEquals(2, messages.size(),
                "Conversation should have exactly 2 messages (1 user + 1 assistant), not " + messages.size());

        // And: First message should be the user message
        assertTrue(messages.get(0) instanceof UserMessage,
                "First message should be UserMessage");
        assertEquals(userMessageText, messages.get(0).getContent(),
                "First message should have the user's text");

        // And: Second message should be the assistant response from the LLM
        assertTrue(messages.get(1) instanceof AssistantMessage,
                "Second message should be AssistantMessage");
        assertFalse(messages.get(1).getContent().isEmpty(),
                "Assistant message should have content from the LLM");
    }

    /**
     * Multi-turn conversation with isolation
     * <p>
     * Verifies that:
     * <ul>
     * <li>Multiple messages can be sent in sequence within a session</li>
     * <li>Conversation history accumulates correctly (multi-turn context retention)</li>
     * <li>Different sessions maintain separate conversation histories (isolation)</li>
     * <li>Messages are ordered correctly in each conversation</li>
     * </ul>
     * <p>
     * This test creates two separate sessions with different conversation IDs,
     * sends 2 messages to each, and verifies that:
     * <ul>
     * <li>Each conversation has exactly 4 messages (2 user + 2 assistant)</li>
     * <li>Messages are in the correct order (user, assistant, user, assistant)</li>
     * <li>No cross-contamination occurs between the two conversations</li>
     * </ul>
     */
    @Test
    void testMultiTurnConversationIsolation() throws InterruptedException {
        // Given: Two separate sessions with different conversation IDs
        BlockingQueue<Message> queue1 = new ArrayBlockingQueue<>(10);
        BlockingQueue<Message> queue2 = new ArrayBlockingQueue<>(10);
        OutputChannel channel1 = new QueueingOutputChannel(queue1);
        OutputChannel channel2 = new QueueingOutputChannel(queue2);

        ChatSession session1 = chatbot.createSession(null, channel1, null, "conversation-1", null);
        ChatSession session2 = chatbot.createSession(null, channel2, null, "conversation-2", null);

        // When: Send 2 messages to each session
        session1.onUserMessage(new UserMessage("Session 1 - Message 1"));
        Message response1a = queue1.poll(10, TimeUnit.SECONDS);
        assertNotNull(response1a, "Session 1 should respond to first message");

        session2.onUserMessage(new UserMessage("Session 2 - Message 1"));
        Message response2a = queue2.poll(10, TimeUnit.SECONDS);
        assertNotNull(response2a, "Session 2 should respond to first message");

        session1.onUserMessage(new UserMessage("Session 1 - Message 2"));
        Message response1b = queue1.poll(10, TimeUnit.SECONDS);
        assertNotNull(response1b, "Session 1 should respond to second message");

        session2.onUserMessage(new UserMessage("Session 2 - Message 2"));
        Message response2b = queue2.poll(10, TimeUnit.SECONDS);
        assertNotNull(response2b, "Session 2 should respond to second message");

        // Then: Each conversation has exactly 4 messages (2 user + 2 assistant)
        var conversation1 = session1.getConversation();
        var conversation2 = session2.getConversation();

        assertEquals(4, conversation1.getMessages().size(),
                "Conversation 1 should have 4 messages (2 user + 2 assistant)");
        assertEquals(4, conversation2.getMessages().size(),
                "Conversation 2 should have 4 messages (2 user + 2 assistant)");

        // And: Messages are in correct order for conversation 1
        var messages1 = conversation1.getMessages();
        assertTrue(messages1.get(0) instanceof UserMessage, "Message 0 should be UserMessage");
        assertEquals("Session 1 - Message 1", messages1.get(0).getContent());
        assertTrue(messages1.get(1) instanceof AssistantMessage, "Message 1 should be AssistantMessage");
        assertTrue(messages1.get(2) instanceof UserMessage, "Message 2 should be UserMessage");
        assertEquals("Session 1 - Message 2", messages1.get(2).getContent());
        assertTrue(messages1.get(3) instanceof AssistantMessage, "Message 3 should be AssistantMessage");

        // And: Messages are in correct order for conversation 2
        var messages2 = conversation2.getMessages();
        assertTrue(messages2.get(0) instanceof UserMessage, "Message 0 should be UserMessage");
        assertEquals("Session 2 - Message 1", messages2.get(0).getContent());
        assertTrue(messages2.get(1) instanceof AssistantMessage, "Message 1 should be AssistantMessage");
        assertTrue(messages2.get(2) instanceof UserMessage, "Message 2 should be UserMessage");
        assertEquals("Session 2 - Message 2", messages2.get(2).getContent());
        assertTrue(messages2.get(3) instanceof AssistantMessage, "Message 3 should be AssistantMessage");

        // And: No cross-contamination between conversations
        assertFalse(messages1.stream().anyMatch(m -> m.getContent().contains("Session 2")),
                "Conversation 1 should not contain Session 2 messages");
        assertFalse(messages2.stream().anyMatch(m -> m.getContent().contains("Session 1")),
                "Conversation 2 should not contain Session 1 messages");
    }

    /**
     * OutputChannel that queues messages for test verification.
     */
    private record QueueingOutputChannel(BlockingQueue<Message> queue) implements OutputChannel {
        @Override
        public void send(OutputChannelEvent event) {
            if (event instanceof MessageOutputChannelEvent msgEvent) {
                queue.offer(msgEvent.getMessage());
            }
        }
    }

}
