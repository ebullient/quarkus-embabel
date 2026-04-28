package io.quarkiverse.embabel.agent.runtime.message;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;

/**
 * Implementation of {@link MessageConverter} that handles bidirectional conversion
 * between Embabel and LangChain4j message formats.
 * <p>
 * This implementation ensures that message content is preserved during conversion
 * and handles all supported message types according to the Embabel specification.
 */
@ApplicationScoped
public class MessageConverterImpl implements MessageConverter {

    /**
     * Converts an Embabel message to a LangChain4j ChatMessage.
     * <p>
     * Currently supports:
     * <ul>
     * <li>{@link com.embabel.chat.UserMessage} → {@link dev.langchain4j.data.message.UserMessage}</li>
     * <li>{@link com.embabel.chat.SystemMessage} → {@link dev.langchain4j.data.message.SystemMessage}</li>
     * <li>{@link com.embabel.chat.AssistantMessage} → {@link dev.langchain4j.data.message.AiMessage}</li>
     * <li>{@link com.embabel.chat.ToolResultMessage} → {@link dev.langchain4j.data.message.ToolExecutionResultMessage}</li>
     * <li>{@link com.embabel.chat.AssistantMessageWithToolCalls} → {@link dev.langchain4j.data.message.AiMessage} (with tool
     * execution requests)</li>
     * </ul>
     *
     * @param embabelMessage the Embabel message to convert
     * @return the corresponding LangChain4j ChatMessage
     * @throws IllegalArgumentException if the message type is not supported
     */
    @Override
    public ChatMessage toLangChain4j(com.embabel.chat.Message embabelMessage) {
        if (embabelMessage == null) {
            throw new IllegalArgumentException("Embabel message cannot be null");
        }

        // Handle UserMessage
        if (embabelMessage instanceof com.embabel.chat.UserMessage userMsg) {
            return new dev.langchain4j.data.message.UserMessage(userMsg.getContent());
        }

        // Handle SystemMessage
        if (embabelMessage instanceof com.embabel.chat.SystemMessage systemMsg) {
            return new dev.langchain4j.data.message.SystemMessage(systemMsg.getContent());
        }

        // Handle AssistantMessageWithToolCalls (must be checked before AssistantMessage)
        if (embabelMessage instanceof com.embabel.chat.AssistantMessageWithToolCalls assistantWithTools) {
            // Convert Embabel ToolCalls to LangChain4j ToolExecutionRequests
            List<ToolExecutionRequest> toolRequests = assistantWithTools.getToolCalls().stream()
                    .map(toolCall -> ToolExecutionRequest.builder()
                            .id(toolCall.getId())
                            .name(toolCall.getName())
                            .arguments(toolCall.getArguments())
                            .build())
                    .collect(Collectors.toList());

            // Create AiMessage with text content (may be empty) and tool execution requests
            String content = assistantWithTools.getContent();
            if (content == null || content.isEmpty()) {
                return new dev.langchain4j.data.message.AiMessage(toolRequests);
            } else {
                return new dev.langchain4j.data.message.AiMessage(content, toolRequests);
            }
        }

        // Handle AssistantMessage (simple, no tools)
        if (embabelMessage instanceof com.embabel.chat.AssistantMessage assistantMsg) {
            return new dev.langchain4j.data.message.AiMessage(assistantMsg.getContent());
        }

        // Handle ToolResultMessage
        if (embabelMessage instanceof com.embabel.chat.ToolResultMessage toolResultMsg) {
            return new dev.langchain4j.data.message.ToolExecutionResultMessage(
                    toolResultMsg.getToolCallId(),
                    toolResultMsg.getToolName(),
                    toolResultMsg.getContent());
        }

        throw new UnsupportedOperationException(
                "Message type not yet implemented: " + embabelMessage.getClass().getName());
    }

    /**
     * Converts a LangChain4j ChatMessage to an Embabel message.
     * <p>
     * Currently supports:
     * <ul>
     * <li>{@link dev.langchain4j.data.message.UserMessage} → {@link com.embabel.chat.UserMessage}</li>
     * <li>{@link dev.langchain4j.data.message.SystemMessage} → {@link com.embabel.chat.SystemMessage}</li>
     * <li>{@link dev.langchain4j.data.message.AiMessage} → {@link com.embabel.chat.AssistantMessage} or
     * {@link com.embabel.chat.AssistantMessageWithToolCalls}</li>
     * <li>{@link dev.langchain4j.data.message.ToolExecutionResultMessage} → {@link com.embabel.chat.ToolResultMessage}</li>
     * </ul>
     *
     * @param lc4jMessage the LangChain4j ChatMessage to convert
     * @return the corresponding Embabel message
     * @throws IllegalArgumentException if the message type is not supported
     */
    @Override
    public com.embabel.chat.Message toEmbabel(ChatMessage lc4jMessage) {
        if (lc4jMessage == null) {
            throw new IllegalArgumentException("LangChain4j message cannot be null");
        }

        // Handle UserMessage
        if (lc4jMessage instanceof dev.langchain4j.data.message.UserMessage userMsg) {
            return new com.embabel.chat.UserMessage(userMsg.singleText());
        }

        // Handle SystemMessage
        if (lc4jMessage instanceof dev.langchain4j.data.message.SystemMessage systemMsg) {
            return new com.embabel.chat.SystemMessage(systemMsg.text());
        }

        // Handle AiMessage (with or without tool calls)
        if (lc4jMessage instanceof dev.langchain4j.data.message.AiMessage aiMsg) {
            // Check if it has tool execution requests
            if (aiMsg.hasToolExecutionRequests()) {
                // Convert to AssistantMessageWithToolCalls
                List<com.embabel.chat.ToolCall> toolCalls = aiMsg.toolExecutionRequests().stream()
                        .map(request -> new com.embabel.chat.ToolCall(
                                request.id(),
                                request.name(),
                                request.arguments()))
                        .collect(Collectors.toList());

                // Get text content (may be null or empty)
                String content = aiMsg.text();
                return new com.embabel.chat.AssistantMessageWithToolCalls(
                        content != null ? content : "",
                        toolCalls);
            } else {
                // Simple AssistantMessage without tools
                return new com.embabel.chat.AssistantMessage(aiMsg.text());
            }
        }

        // Handle ToolExecutionResultMessage
        if (lc4jMessage instanceof dev.langchain4j.data.message.ToolExecutionResultMessage toolResultMsg) {
            return new com.embabel.chat.ToolResultMessage(
                    toolResultMsg.id(),
                    toolResultMsg.toolName(),
                    toolResultMsg.text());
        }

        throw new UnsupportedOperationException(
                "Message type not yet implemented: " + lc4jMessage.getClass().getName());
    }
}