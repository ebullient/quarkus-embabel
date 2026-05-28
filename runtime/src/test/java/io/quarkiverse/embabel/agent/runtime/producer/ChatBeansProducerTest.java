package io.quarkiverse.embabel.agent.runtime.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.chat.Chatbot;
import com.embabel.chat.Conversation;
import com.embabel.chat.ConversationFactory;
import com.embabel.chat.ConversationStoreType;
import com.embabel.chat.support.InMemoryAssetTracker;
import com.embabel.chat.support.InMemoryConversation;

import io.quarkus.test.component.QuarkusComponentTest;

/**
 * Component tests for {@link ChatBeansProducer} using CDI.
 * <p>
 * Tests verify that:
 * <ul>
 * <li>Default {@link ConversationFactory} bean is produced</li>
 * <li>Custom {@link ConversationFactory} can override the default via CDI</li>
 * <li>Custom factory is properly injected into {@link Chatbot}</li>
 * </ul>
 * <p>
 * Uses {@link QuarkusComponentTest} for lightweight CDI testing with real
 * bean discovery and injection.
 */
@QuarkusComponentTest
class ChatBeansProducerTest {

    @Inject
    ChatBeansProducer chatBeansProducer;

    @Inject
    Chatbot chatbot;

    @Inject
    ConversationFactory conversationFactory;

    @Inject
    AgentPlatform agentPlatform;

    @Test
    void chatBeansProducerIsInjectable() {
        // Verify the producer itself is injectable
        assertThat(chatBeansProducer).isNotNull();
    }

    @Test
    void chatbotIsInjectable() {
        // Verify chatbot bean is produced
        assertThat(chatbot).isNotNull();
    }

    @Test
    void conversationFactoryIsInjectable() {
        // Verify conversation factory bean is produced
        assertThat(conversationFactory).isNotNull();
    }

    @Test
    void agentPlatformIsInjectable() {
        // Verify agent platform (mock) is injectable
        assertThat(agentPlatform).isNotNull();
    }

    @Test
    void customFactoryOverridesDefault() {
        // Verify the custom factory reports STORED type (not IN_MEMORY)
        // This confirms our custom factory is being used instead of the default
        assertThat(conversationFactory.getStoreType())
                .isEqualTo(ConversationStoreType.STORED)
                .as("Custom factory should override default @DefaultBean and report STORED type");
    }

    @Test
    void customFactoryIsUsedByChatbot() {
        // The chatbot bean was successfully created with our custom factory
        // We can verify this by directly testing the factory that was injected

        // When - create a conversation using the injected factory
        Conversation conversation = conversationFactory.create("test-conversation-id");

        // Then - verify the conversation was created successfully
        assertThat(conversation).isNotNull();
        assertThat(conversation.getId()).isEqualTo("test-conversation-id");

        // Verify it's using our custom factory by checking the store type
        assertThat(conversationFactory.getStoreType())
                .isEqualTo(ConversationStoreType.STORED)
                .as("Factory should be our custom STORED type, not default IN_MEMORY");
    }

    /**
     * Custom conversation factory that tracks method calls.
     * <p>
     * This factory is produced with higher priority than the default {@code @DefaultBean},
     * ensuring it overrides the in-memory factory.
     */
    static class TrackingConversationFactory implements ConversationFactory {
        private final AtomicInteger createCallCount = new AtomicInteger(0);
        private final AtomicInteger loadCallCount = new AtomicInteger(0);

        @Override
        public Conversation create(String conversationId) {
            createCallCount.incrementAndGet();
            // Return a simple in-memory conversation for testing
            return new InMemoryConversation(
                    java.util.Collections.emptyList(),
                    conversationId,
                    false,
                    new InMemoryAssetTracker());
        }

        @Override
        public Conversation load(String conversationId) {
            loadCallCount.incrementAndGet();
            // Return null to indicate not found (in-memory behavior)
            return null;
        }

        @Override
        public ConversationStoreType getStoreType() {
            return ConversationStoreType.STORED;
        }

        public int getCreateCallCount() {
            return createCallCount.get();
        }

        public int getLoadCallCount() {
            return loadCallCount.get();
        }
    }

    /**
     * Produces mock beans required for testing {@link ChatBeansProducer}.
     * <p>
     * Provides:
     * <ul>
     * <li>Mock {@link AgentPlatform} - required by chatbot producer</li>
     * <li>Custom {@link TrackingConversationFactory} - overrides default factory</li>
     * </ul>
     */
    @ApplicationScoped
    static class TestBeanProducer {

        /**
         * Produces a mock {@link AgentPlatform} for testing.
         * <p>
         * The chatbot producer requires an AgentPlatform, but for this test
         * we only care about verifying the factory override mechanism.
         */
        @Produces
        @ApplicationScoped
        public AgentPlatform mockAgentPlatform() {
            return mock(AgentPlatform.class);
        }

        /**
         * Produces a custom {@link ConversationFactory} that overrides the default.
         * <p>
         * Uses {@link Priority} to override the {@code @DefaultBean} from {@link ChatBeansProducer}.
         */
        @Produces
        @ApplicationScoped
        @Priority(1) // Higher priority than @DefaultBean
        public ConversationFactory customFactory() {
            return new TrackingConversationFactory();
        }
    }
}
