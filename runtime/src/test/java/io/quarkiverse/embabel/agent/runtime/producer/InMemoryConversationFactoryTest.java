package io.quarkiverse.embabel.agent.runtime.producer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.chat.Conversation;
import com.embabel.chat.ConversationFactory;
import com.embabel.chat.ConversationStoreType;

import io.quarkus.test.component.QuarkusComponentTest;

/**
 * Component test for InMemoryConversationFactory behavior in quarkus-embabel.
 * <p>
 * This test verifies the semantics of the default in-memory conversation factory
 * provided by the extension. The in-memory factory is suitable for development,
 * testing, and stateless scenarios where conversation persistence is not required.
 *
 * @see com.embabel.chat.support.InMemoryConversationFactory
 * @see com.embabel.chat.ConversationFactory
 */
@QuarkusComponentTest
class InMemoryConversationFactoryTest {

    @Inject
    ChatBeansProducer chatBeansProducer;

    @Inject
    ConversationFactory conversationFactory;

    /**
     * Verify store type is IN_MEMORY.
     * <p>
     * The default factory provided by the extension should be an in-memory implementation,
     * suitable for development, testing, and stateless scenarios.
     */
    @Test
    void testStoreTypeIsInMemory() {
        assertEquals(ConversationStoreType.IN_MEMORY, conversationFactory.getStoreType(),
                "Default ConversationFactory should be IN_MEMORY type");
    }

    /**
     * Verify created conversation has the requested ID.
     * <p>
     * When creating a conversation with a specific ID, the factory should honor
     * that ID rather than generating a new one.
     */
    @Test
    void testCreatedConversationHasRequestedId() {
        String conversationId = "test-conversation-456";

        Conversation conversation = conversationFactory.create(conversationId);

        assertNotNull(conversation, "Created conversation should not be null");
        assertEquals(conversationId, conversation.getId(),
                "Conversation should have the requested ID");
    }

    /**
     * Verify conversation starts empty.
     * <p>
     * Newly created conversations should have no messages in their history.
     */
    @Test
    void testConversationStartsEmpty() {
        String conversationId = "empty-conversation-789";

        Conversation conversation = conversationFactory.create(conversationId);

        assertNotNull(conversation.getMessages(), "Messages list should not be null");
        assertTrue(conversation.getMessages().isEmpty(),
                "Newly created conversation should have no messages");
    }

    /**
     * Verify conversations are not persistent.
     * <p>
     * The in-memory factory does not persist conversations, so attempting to load
     * a conversation should always return null, even immediately after creating it.
     * <p>
     * This is the key distinction from persistent factory implementations which
     * would return the conversation from their backing store.
     */
    @Test
    void testLoadReturnsNull() {
        String conversationId = "transient-conversation-999";
        Conversation created = conversationFactory.create(conversationId);
        assertNotNull(created, "Conversation should be created");

        Conversation loaded = conversationFactory.load(conversationId);

        assertNull(loaded, "InMemoryConversationFactory.load() should always return null");
    }

    /**
     * Verify load returns null for non-existent conversations.
     * <p>
     * Even when loading a conversation ID that was never created,
     * the in-memory factory should return null (same behavior as persistent
     * factories when the conversation is not found).
     */
    @Test
    void testLoadReturnsNullForNonExistentConversation() {
        String nonExistentId = "never-created-conversation";

        Conversation loaded = conversationFactory.load(nonExistentId);

        assertNull(loaded, "Loading non-existent conversation should return null");
    }

    /**
     * Produces mock beans required for testing the default {@link ConversationFactory}.
     * <p>
     * Provides:
     * <ul>
     * <li>Mock {@link AgentPlatform} - required by chatbot producer</li>
     * </ul>
     */
    @ApplicationScoped
    static class TestBeanProducer {

        /**
         * Produces a mock {@link AgentPlatform} for testing.
         * <p>
         * The chatbot producer requires an AgentPlatform, but for this test
         * we only care about verifying the default factory behavior.
         */
        @Produces
        @ApplicationScoped
        public AgentPlatform mockAgentPlatform() {
            return mock(AgentPlatform.class);
        }
    }
}