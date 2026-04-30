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

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.embabel.agent.runtime.message.MessageConverter;
import io.quarkiverse.embabel.agent.runtime.tool.ToolSpecificationConverter;

/**
 * Quarkus implementation of {@link LlmMessageSender} that uses LangChain4j's {@link ChatModel}.
 * <p>
 * This class handles the conversion between Embabel and LangChain4j message formats,
 * makes LLM calls using the configured ChatModel, and converts responses back to Embabel format.
 * <p>
 * This implementation supports both tool and non-tool calls.
 *
 * @see LlmMessageSender
 * @see ChatModel
 */
public class QuarkusLlmMessageSender implements LlmMessageSender {

    private final ChatModel chatModel;
    private final LlmOptions options;
    private final MessageConverter messageConverter;
    private final ToolSpecificationConverter toolConverter;

    /**
     * Creates a new QuarkusLlmMessageSender.
     *
     * @param chatModel the LangChain4j ChatModel to use for LLM calls
     * @param options the LLM options (temperature, max tokens, etc.)
     * @param messageConverter the converter for message format translation
     * @param toolConverter the converter for tool specification translation
     */
    public QuarkusLlmMessageSender(
            ChatModel chatModel,
            LlmOptions options,
            MessageConverter messageConverter,
            ToolSpecificationConverter toolConverter) {
        this.chatModel = Objects.requireNonNull(chatModel, "ChatModel cannot be null");
        this.options = Objects.requireNonNull(options, "LlmOptions cannot be null");
        this.messageConverter = Objects.requireNonNull(messageConverter, "MessageConverter cannot be null");
        this.toolConverter = Objects.requireNonNull(toolConverter, "ToolSpecificationConverter cannot be null");
    }

    /**
     * Makes an LLM call with the given messages and optional tools.
     * <p>
     * This implementation:
     * <ol>
     * <li>Converts Embabel messages to LangChain4j format</li>
     * <li>Converts tool specifications if tools are provided</li>
     * <li>Calls the LLM using the configured ChatModel</li>
     * <li>Converts the response back to Embabel format</li>
     * <li>Extracts and converts token usage information</li>
     * </ol>
     * <p>
     * When tools are provided, the LLM may respond with tool execution requests.
     * The response message will be an {@link com.embabel.chat.AssistantMessageWithToolCalls}
     * containing the tool calls to execute.
     *
     * @param messages the conversation history
     * @param tools the available tools (empty list if no tools)
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

        // Build the chat request
        ChatRequest.Builder requestBuilder = ChatRequest.builder()
                .messages(lc4jMessages);

        // Add tool specifications if tools are provided
        if (!tools.isEmpty()) {
            List<ToolSpecification> toolSpecs = tools.stream()
                    .map(toolConverter::toLangChain4j)
                    .collect(Collectors.toList());
            requestBuilder.toolSpecifications(toolSpecs);
        }

        // Call the LLM
        ChatResponse response = chatModel.chat(requestBuilder.build());

        // Extract the AI message from the response
        AiMessage aiMessage = response.aiMessage();

        // Convert response (may include tool calls)
        Message embabelMessage = messageConverter.toEmbabel(aiMessage);

        // Extract text content (use empty string if null, as when only tool calls are present)
        String textContent = aiMessage.text() != null ? aiMessage.text() : "";

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
                inputTokens,
                outputTokens,
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

    /**
     * Gets the tool specification converter used by this sender.
     * <p>
     * Exposed for testing purposes.
     *
     * @return the tool specification converter
     */
    ToolSpecificationConverter getToolConverter() {
        return toolConverter;
    }
}