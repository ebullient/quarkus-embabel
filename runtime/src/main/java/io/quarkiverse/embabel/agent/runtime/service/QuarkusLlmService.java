package io.quarkiverse.embabel.agent.runtime.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.embabel.agent.spi.LlmService;
import com.embabel.agent.spi.loop.LlmMessageSender;
import com.embabel.agent.spi.loop.streaming.LlmMessageStreamer;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.model.PricingModel;
import com.embabel.common.ai.prompt.KnowledgeCutoffDate;
import com.embabel.common.ai.prompt.PromptContributor;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.quarkiverse.embabel.agent.runtime.message.MessageConverter;
import io.quarkiverse.embabel.agent.runtime.message.MessageConverterImpl;
import io.quarkiverse.embabel.agent.runtime.tool.ToolSpecificationConverter;
import io.quarkiverse.embabel.agent.runtime.tool.ToolSpecificationConverterImpl;

/**
 * Quarkus implementation of {@link LlmService} that uses LangChain4j's {@link ChatModel}.
 * <p>
 * This service wraps a LangChain4j chat model and provides the Embabel SPI interface for
 * creating message senders and managing LLM metadata. Instances are created synthetically
 * at build time for each configured ChatModel, supporting multiple models in a single application.
 * <p>
 * Configuration example for multiple models:
 *
 * <pre>
 * # Default OpenAI model
 * quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
 * quarkus.langchain4j.openai.chat-model.model-name=gpt-4o
 *
 * # Named "fast" model
 * quarkus.langchain4j.openai.fast.api-key=${OPENAI_API_KEY}
 * quarkus.langchain4j.openai.fast.chat-model.model-name=gpt-4o-mini
 *
 * # Named "claude" model
 * quarkus.langchain4j.anthropic.claude.api-key=${ANTHROPIC_API_KEY}
 * quarkus.langchain4j.anthropic.claude.chat-model.model-name=claude-3-5-sonnet
 * </pre>
 *
 * @see LlmService
 * @see ChatModel
 */
public class QuarkusLlmService implements LlmService<QuarkusLlmService> {

    private final String name;
    private final String provider;
    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final LocalDate knowledgeCutoffDate;
    private final List<PromptContributor> promptContributors;
    private final PricingModel pricingModel;

    /**
     * Constructor for synthetic bean creation.
     * <p>
     * This constructor is called by the build-time recorder to create instances
     * for each configured ChatModel. CDI ensures the ChatModel bean is available
     * before creating the LlmService bean.
     *
     * @param name the model name (e.g., "gpt-4o", "claude-3-5-sonnet")
     * @param provider the provider name (e.g., "openai", "anthropic", "ollama")
     * @param chatModel the ChatModel instance from quarkus-langchain4j (injected by CDI)
     */
    public QuarkusLlmService(String name, String provider, ChatModel chatModel) {
        this(name, provider, chatModel, null, null, Collections.emptyList(), null);
    }

    /**
     * Constructor with optional streaming model support.
     *
     * @param name the model name
     * @param provider the provider name
     * @param chatModel the synchronous chat model
     * @param streamingChatModel the streaming chat model, or null if unavailable
     */
    public QuarkusLlmService(String name, String provider, ChatModel chatModel, StreamingChatModel streamingChatModel) {
        this(name, provider, chatModel, streamingChatModel, null, Collections.emptyList(), null);
    }

    /**
     * Private constructor for creating copies with updated metadata.
     *
     * @param name the model name
     * @param provider the provider name
     * @param chatModel the chat language model
     * @param streamingChatModel the streaming chat model (optional)
     * @param knowledgeCutoffDate the knowledge cutoff date (optional)
     * @param promptContributors the list of prompt contributors
     * @param pricingModel the pricing model (optional)
     */
    private QuarkusLlmService(
            String name,
            String provider,
            ChatModel chatModel,
            StreamingChatModel streamingChatModel,
            LocalDate knowledgeCutoffDate,
            List<PromptContributor> promptContributors,
            PricingModel pricingModel) {
        this.name = Objects.requireNonNull(name, "Model name cannot be null");
        this.provider = Objects.requireNonNull(provider, "Provider cannot be null");
        this.chatModel = Objects.requireNonNull(chatModel, "ChatModel cannot be null");
        this.streamingChatModel = streamingChatModel;
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
     * Returns a {@link QuarkusLlmMessageSender} that wraps the LangChain4j ChatModel
     * and handles message conversion between Embabel and LangChain4j formats.
     *
     * @param options the LLM options (temperature, max tokens, etc.)
     * @return a message sender configured with the given options
     */
    @Override
    public LlmMessageSender createMessageSender(LlmOptions options) {
        Objects.requireNonNull(options, "LlmOptions cannot be null");
        MessageConverter messageConverter = new MessageConverterImpl();
        ToolSpecificationConverter toolConverter = new ToolSpecificationConverterImpl();
        return new QuarkusLlmMessageSender(chatModel, options, messageConverter, toolConverter);
    }

    /**
     * Creates a message streamer for streaming LLM calls.
     *
     * @param options the LLM options
     * @return a message streamer
     * @throws UnsupportedOperationException if no streaming model is available
     */
    @Override
    public LlmMessageStreamer createMessageStreamer(LlmOptions options) {
        Objects.requireNonNull(options, "LlmOptions cannot be null");
        if (streamingChatModel == null) {
            throw new UnsupportedOperationException("Streaming support is not available for this LLM service");
        }

        MessageConverter messageConverter = new MessageConverterImpl();
        QuarkusStreamingLlmService streamingService = new QuarkusStreamingLlmService(streamingChatModel, messageConverter);
        return (messages, tools, toolCallInspectors) -> streamingService.streamResponse(messages);
    }

    /**
     * Checks if this LLM service supports streaming operations.
     *
     * @return true when a streaming model is available
     */
    @Override
    public boolean supportsStreaming() {
        return streamingChatModel != null;
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
                streamingChatModel,
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
                streamingChatModel,
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
                streamingChatModel,
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