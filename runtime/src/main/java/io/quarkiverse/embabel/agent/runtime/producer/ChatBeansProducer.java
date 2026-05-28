package io.quarkiverse.embabel.agent.runtime.producer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.Verbosity;
import com.embabel.chat.Chatbot;
import com.embabel.chat.ConversationFactory;
import com.embabel.chat.agent.AgentProcessChatbot;
import com.embabel.chat.support.InMemoryConversationFactory;

import io.quarkus.arc.DefaultBean;

/**
 * CDI producer for Embabel Chat API beans.
 * <p>
 * This producer creates the chat-related dependencies required for conversational agent flows:
 * <ul>
 * <li>{@link ConversationFactory} - Factory for creating and loading conversations (default: in-memory)</li>
 * <li>{@link Chatbot} - Main chat interface backed by {@link AgentProcessChatbot}</li>
 * </ul>
 * <p>
 * The default {@link InMemoryConversationFactory} can be overridden by providing a custom
 * {@link ConversationFactory} bean in the application. This allows for persistent conversation
 * storage using databases, Redis, or other storage mechanisms.
 * <p>
 * Following Embabel's design, the conversation factory is a constructor parameter when creating
 * the chatbot, not runtime configuration. Applications choose the factory when building the
 * chatbot via CDI bean override.
 *
 * @see AgentProcessChatbot
 * @see InMemoryConversationFactory
 * @see ConversationFactory
 */
@ApplicationScoped
public class ChatBeansProducer {

    /**
     * Produces a default {@link ConversationFactory} using in-memory storage.
     * <p>
     * This factory creates conversations that are stored in memory only and are not persisted.
     * Suitable for development, testing, and stateless scenarios.
     * <p>
     * Applications can override this by providing their own {@link ConversationFactory} bean
     * for persistent storage (e.g., JPA/Hibernate, Redis, MongoDB).
     *
     * @return the in-memory conversation factory
     */
    @Produces
    @DefaultBean
    @ApplicationScoped
    public ConversationFactory inMemoryConversationFactory() {
        return new InMemoryConversationFactory();
    }

    /**
     * Produces a {@link Chatbot} implementation backed by {@link AgentProcessChatbot}.
     * <p>
     * The chatbot uses utility-based planning and integrates with the injected
     * {@link AgentPlatform} and {@link ConversationFactory}. The conversation factory
     * is resolved via CDI, allowing applications to override the default in-memory
     * factory with custom implementations.
     * <p>
     * The chatbot is created using
     * {@link AgentProcessChatbot#utilityFromPlatform(AgentPlatform, ConversationFactory, Verbosity, com.embabel.chat.agent.ListenerProvider)}
     * which configures it to use all actions available on the platform with utility-based planning.
     *
     * @param agentPlatform the agent platform for creating and managing agent processes
     * @param conversationFactory the conversation factory (resolved via CDI, defaults to in-memory)
     * @return the chatbot instance
     */
    @Produces
    @DefaultBean
    @ApplicationScoped
    public Chatbot chatbot(AgentPlatform agentPlatform, ConversationFactory conversationFactory) {
        // Create a simple listener provider that returns no listeners
        // Applications can override this by providing their own Chatbot bean
        return AgentProcessChatbot.utilityFromPlatform(
                agentPlatform,
                conversationFactory,
                new Verbosity(),
                (user, outputChannel) -> java.util.Collections.emptyList());
    }
}