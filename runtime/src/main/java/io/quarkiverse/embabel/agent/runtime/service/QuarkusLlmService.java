package io.quarkiverse.embabel.agent.runtime.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.embabel.agent.spi.LlmService;
import com.embabel.agent.spi.loop.LlmMessageSender;
import com.embabel.agent.spi.loop.streaming.LlmMessageStreamer;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.model.PricingModel;
import com.embabel.common.ai.prompt.KnowledgeCutoffDate;
import com.embabel.common.ai.prompt.PromptContributor;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Quarkus implementation of {@link LlmService} that uses LangChain4j's {@link ChatModel}.
 * <p>
 * This service wraps a LangChain4j chat model and provides the Embabel SPI interface for
 * creating message senders and managing LLM metadata. The underlying {@link ChatModel}
 * is automatically injected by quarkus-langchain4j based on configuration.
 * <p>
 * Configuration example:
 *
 * <pre>
 * quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
 * quarkus.langchain4j.openai.chat-model.model-name=gpt-4o
 * quarkus.langchain4j.openai.chat-model.temperature=0.7
 * </pre>
 *
 * @see LlmService
 * @see ChatModel
 */
@ApplicationScoped
public class QuarkusLlmService implements LlmService<QuarkusLlmService> {

    private final String name;
    private final String provider;
    private final ChatModel chatModel;
    private final LocalDate knowledgeCutoffDate;
    private final List<PromptContributor> promptContributors;
    private final PricingModel pricingModel;

    /**
     * Constructor for CDI injection.
     * <p>
     * The model name and provider are read from configuration. The provider should match
     * the quarkus-langchain4j provider being used (openai, anthropic, ollama, etc.).
     *
     * @param modelName the name of the model from configuration
     * @param provider the provider name (openai, anthropic, ollama, etc.)
     * @param chatModel the injected ChatModel from quarkus-langchain4j
     */
    @Inject
    public QuarkusLlmService(
            @ConfigProperty(name = "quarkus.langchain4j.chat-model.model-name") String modelName,
            @ConfigProperty(name = "quarkus.langchain4j.chat-model.provider", defaultValue = "openai") String provider,
            ChatModel chatModel) {
        this(modelName, provider, chatModel, null, Collections.emptyList(), null);
    }

    /**
     * Private constructor for creating copies with updated metadata.
     *
     * @param name the model name
     * @param provider the provider name
     * @param chatModel the chat language model
     * @param knowledgeCutoffDate the knowledge cutoff date (optional)
     * @param promptContributors the list of prompt contributors
     * @param pricingModel the pricing model (optional)
     */
    private QuarkusLlmService(
            String name,
            String provider,
            ChatModel chatModel,
            LocalDate knowledgeCutoffDate,
            List<PromptContributor> promptContributors,
            PricingModel pricingModel) {
        this.name = Objects.requireNonNull(name, "Model name cannot be null");
        this.provider = Objects.requireNonNull(provider, "Provider cannot be null");
        this.chatModel = Objects.requireNonNull(chatModel, "ChatModel cannot be null");
        this.knowledgeCutoffDate = knowledgeCutoffDate;
        this.promptContributors = new ArrayList<>(promptContributors);
        this.pricingModel = pricingModel;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getProvider() {
        return provider;
    }

    @Override
    public LocalDate getKnowledgeCutoffDate() {
        return knowledgeCutoffDate;
    }

    @Override
    public PricingModel getPricingModel() {
        return pricingModel;
    }

    @Override
    public List<PromptContributor> getPromptContributors() {
        return Collections.unmodifiableList(promptContributors);
    }

    /**
     * Creates a message sender for making LLM calls with the specified options.
     * <p>
     * The message sender will be implemented in Step 13.
     *
     * @param options the LLM options (temperature, max tokens, etc.)
     * @return a message sender configured with the given options
     */
    @Override
    public LlmMessageSender createMessageSender(LlmOptions options) {
        // Will be implemented in Step 13
        throw new UnsupportedOperationException("QuarkusLlmMessageSender not yet implemented - Step 13");
    }

    /**
     * Creates a message streamer for streaming LLM calls.
     * <p>
     * Streaming support will be implemented in Phase 9 (Steps 26-27).
     *
     * @param options the LLM options
     * @return a message streamer
     * @throws UnsupportedOperationException streaming not yet implemented
     */
    @Override
    public LlmMessageStreamer createMessageStreamer(LlmOptions options) {
        throw new UnsupportedOperationException("Streaming support not yet implemented - Phase 9");
    }

    /**
     * Checks if this LLM service supports streaming operations.
     * <p>
     * Currently returns false. Streaming support will be added in Phase 9.
     *
     * @return false (streaming not yet supported)
     */
    @Override
    public boolean supportsStreaming() {
        return false;
    }

    /**
     * Returns a copy of this service with the specified knowledge cutoff date.
     * <p>
     * The knowledge cutoff date is automatically added to the prompt contributors.
     *
     * @param date the knowledge cutoff date
     * @return a new instance with the updated cutoff date
     */
    @Override
    public QuarkusLlmService withKnowledgeCutoffDate(LocalDate date) {
        Objects.requireNonNull(date, "Knowledge cutoff date cannot be null");
        List<PromptContributor> updatedContributors = new ArrayList<>(promptContributors);
        updatedContributors.add(new KnowledgeCutoffDate(date, DateTimeFormatter.ISO_LOCAL_DATE));
        return new QuarkusLlmService(
                name,
                provider,
                chatModel,
                date,
                updatedContributors,
                pricingModel);
    }

    /**
     * Returns a copy of this service with an additional prompt contributor.
     *
     * @param promptContributor the prompt contributor to add
     * @return a new instance with the added contributor
     */
    @Override
    public QuarkusLlmService withPromptContributor(PromptContributor promptContributor) {
        Objects.requireNonNull(promptContributor, "Prompt contributor cannot be null");
        List<PromptContributor> updatedContributors = new ArrayList<>(promptContributors);
        updatedContributors.add(promptContributor);
        return new QuarkusLlmService(
                name,
                provider,
                chatModel,
                knowledgeCutoffDate,
                updatedContributors,
                pricingModel);
    }

    /**
     * Returns a copy of this service with the specified pricing model.
     *
     * @param pricingModel the pricing model
     * @return a new instance with the updated pricing model
     */
    public QuarkusLlmService withPricingModel(PricingModel pricingModel) {
        return new QuarkusLlmService(
                name,
                provider,
                chatModel,
                knowledgeCutoffDate,
                promptContributors,
                pricingModel);
    }

    /**
     * Gets the underlying LangChain4j ChatModel.
     * <p>
     * This is exposed for internal use by message senders and other components.
     *
     * @return the chat language model
     */
    public ChatModel getChatModel() {
        return chatModel;
    }
}