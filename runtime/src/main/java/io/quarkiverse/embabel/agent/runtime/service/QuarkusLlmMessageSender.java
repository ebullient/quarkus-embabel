package io.quarkiverse.embabel.agent.runtime.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.Usage;
import com.embabel.agent.spi.loop.LlmMessageResponse;
import com.embabel.agent.spi.loop.LlmMessageSender;
import com.embabel.chat.Message;
import com.embabel.common.ai.model.LlmOptions;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.embabel.agent.runtime.message.MessageConverter;

/**
 * Quarkus implementation of {@link LlmMessageSender} that uses LangChain4j's {@link ChatModel}.
 * <p>
 * This class handles the conversion between Embabel and LangChain4j message formats,
 * makes LLM calls using the configured ChatModel, and converts responses back to Embabel format.
 * <p>
 * This implementation supports non-tool calls. Tool support will be added in Step 14.
 *
 * @see LlmMessageSender
 * @see ChatModel
 */
public class QuarkusLlmMessageSender implements LlmMessageSender {

    private final ChatModel chatModel;
    private final LlmOptions options;
    private final MessageConverter messageConverter;

    /**
     * Creates a new QuarkusLlmMessageSender.
     *
     * @param chatModel the LangChain4j ChatModel to use for LLM calls
     * @param options the LLM options (temperature, max tokens, etc.)
     * @param messageConverter the converter for message format translation
     */
    public QuarkusLlmMessageSender(
            ChatModel chatModel,
            LlmOptions options,
            MessageConverter messageConverter) {
        this.chatModel = Objects.requireNonNull(chatModel, "ChatModel cannot be null");
        this.options = Objects.requireNonNull(options, "LlmOptions cannot be null");
        this.messageConverter = Objects.requireNonNull(messageConverter, "MessageConverter cannot be null");
    }

    /**
     * Makes an LLM call with the given messages.
     * <p>
     * This implementation:
     * <ol>
     * <li>Converts Embabel messages to LangChain4j format</li>
     * <li>Calls the LLM using the configured ChatModel</li>
     * <li>Converts the response back to Embabel format</li>
     * <li>Extracts and converts token usage information</li>
     * </ol>
     * <p>
     * Tool support will be added in Step 14. Currently, the tools parameter is ignored.
     *
     * @param messages the conversation history
     * @param tools the available tools (currently ignored - Step 14)
     * @return the LLM response with message, text, and usage information
     */
    @Override
    public LlmMessageResponse call(List<? extends Message> messages, List<? extends Tool> tools) {
        Objects.requireNonNull(messages, "Messages cannot be null");
        Objects.requireNonNull(tools, "Tools cannot be null");

        // Convert Embabel messages to LangChain4j format
        List<ChatMessage> lc4jMessages = messages.stream()
                .map(messageConverter::toLangChain4j)
                .collect(Collectors.toList());

        // Call the LLM (ChatModel is already configured by quarkus-langchain4j)
        // Note: Tool support will be added in Step 14
        ChatResponse response = chatModel.chat(lc4jMessages);

        // Extract the AI message from the response
        AiMessage aiMessage = response.aiMessage();

        // Convert the response back to Embabel format
        Message embabelMessage = messageConverter.toEmbabel(aiMessage);

        // Extract text content
        String textContent = aiMessage.text();

        // Convert token usage
        Usage usage = convertUsage(response.metadata().tokenUsage());

        return new LlmMessageResponse(
                embabelMessage,
                textContent,
                usage);
    }

    /**
     * Converts LangChain4j TokenUsage to Embabel Usage.
     * <p>
     * Returns null if the token usage information is not available.
     *
     * @param tokenUsage the LangChain4j token usage (may be null)
     * @return the Embabel usage object, or null if token usage is not available
     */
    private Usage convertUsage(TokenUsage tokenUsage) {
        if (tokenUsage == null) {
            return null;
        }

        Integer inputTokens = tokenUsage.inputTokenCount();
        Integer outputTokens = tokenUsage.outputTokenCount();

        // Only create Usage if we have at least one token count
        if (inputTokens == null && outputTokens == null) {
            return null;
        }

        return new Usage(
                inputTokens != null ? inputTokens : 0,
                outputTokens != null ? outputTokens : 0,
                tokenUsage);
    }

    /**
     * Gets the ChatModel used by this sender.
     * <p>
     * Exposed for testing purposes.
     *
     * @return the chat model
     */
    ChatModel getChatModel() {
        return chatModel;
    }

    /**
     * Gets the LLM options used by this sender.
     * <p>
     * Exposed for testing purposes.
     *
     * @return the LLM options
     */
    LlmOptions getOptions() {
        return options;
    }

    /**
     * Gets the message converter used by this sender.
     * <p>
     * Exposed for testing purposes.
     *
     * @return the message converter
     */
    MessageConverter getMessageConverter() {
        return messageConverter;
    }
}