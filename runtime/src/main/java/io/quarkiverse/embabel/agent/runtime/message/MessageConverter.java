package io.quarkiverse.embabel.agent.runtime.message;

import dev.langchain4j.data.message.ChatMessage;

/**
 * Interface for converting messages between Embabel and LangChain4j formats.
 * <p>
 * This converter handles bidirectional conversion of messages, supporting all message types
 * including user messages, assistant messages, system messages, tool results, and assistant
 * messages with tool calls.
 * <p>
 * Implementations must handle:
 * <ul>
 * <li>Text content conversion</li>
 * <li>Multimodal content (images, etc.) where supported</li>
 * <li>Tool call information</li>
 * <li>Tool execution results</li>
 * <li>Message metadata (timestamps, names, etc.)</li>
 * </ul>
 */
public interface MessageConverter {

    /**
     * Converts an Embabel message to a LangChain4j ChatMessage.
     * <p>
     * This method handles conversion from Embabel's message types:
     * <ul>
     * <li>{@link com.embabel.chat.UserMessage} → {@link dev.langchain4j.data.message.UserMessage}</li>
     * <li>{@link com.embabel.chat.AssistantMessage} → {@link dev.langchain4j.data.message.AiMessage}</li>
     * <li>{@link com.embabel.chat.SystemMessage} → {@link dev.langchain4j.data.message.SystemMessage}</li>
     * <li>{@link com.embabel.chat.ToolResultMessage} → {@link dev.langchain4j.data.message.ToolExecutionResultMessage}</li>
     * <li>{@link com.embabel.chat.AssistantMessageWithToolCalls} → {@link dev.langchain4j.data.message.AiMessage} (with tool
     * execution requests)</li>
     * </ul>
     *
     * @param embabelMessage the Embabel message to convert
     * @return the corresponding LangChain4j ChatMessage
     * @throws IllegalArgumentException if the message type is not supported or conversion fails
     */
    ChatMessage toLangChain4j(com.embabel.chat.Message embabelMessage);

    /**
     * Converts a LangChain4j ChatMessage to an Embabel message.
     * <p>
     * This method handles conversion from LangChain4j's message types:
     * <ul>
     * <li>{@link dev.langchain4j.data.message.UserMessage} → {@link com.embabel.chat.UserMessage}</li>
     * <li>{@link dev.langchain4j.data.message.AiMessage} → {@link com.embabel.chat.AssistantMessage} or
     * {@link com.embabel.chat.AssistantMessageWithToolCalls}</li>
     * <li>{@link dev.langchain4j.data.message.SystemMessage} → {@link com.embabel.chat.SystemMessage}</li>
     * <li>{@link dev.langchain4j.data.message.ToolExecutionResultMessage} → {@link com.embabel.chat.ToolResultMessage}</li>
     * </ul>
     *
     * @param lc4jMessage the LangChain4j ChatMessage to convert
     * @return the corresponding Embabel message
     * @throws IllegalArgumentException if the message type is not supported or conversion fails
     */
    com.embabel.chat.Message toEmbabel(ChatMessage lc4jMessage);
}