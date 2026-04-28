package io.quarkiverse.embabel.agent.runtime.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.ChatMessage;

/**
 * Unit tests for {@link MessageConverterImpl} focusing on UserMessage conversion.
 * <p>
 * Tests verify:
 * <ul>
 * <li>Embabel UserMessage → LangChain4j UserMessage conversion</li>
 * <li>LangChain4j UserMessage → Embabel UserMessage conversion</li>
 * <li>Round-trip conversion preserves content</li>
 * <li>Null handling</li>
 * <li>Error cases</li>
 * </ul>
 */
class MessageConverterImplTest {

    private MessageConverterImpl converter;

    @BeforeEach
    void setUp() {
        converter = new MessageConverterImpl();
    }

    @Test
    @DisplayName("Should convert Embabel UserMessage to LangChain4j UserMessage")
    void shouldConvertEmbabelUserMessageToLangChain4j() {
        // Given
        String content = "Hello, how can you help me?";
        com.embabel.chat.UserMessage embabelMsg = new com.embabel.chat.UserMessage(content);

        // When
        ChatMessage lc4jMsg = converter.toLangChain4j(embabelMsg);

        // Then
        assertThat(lc4jMsg).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        dev.langchain4j.data.message.UserMessage userMsg = (dev.langchain4j.data.message.UserMessage) lc4jMsg;
        assertThat(userMsg.singleText()).isEqualTo(content);
    }

    @Test
    @DisplayName("Should convert LangChain4j UserMessage to Embabel UserMessage")
    void shouldConvertLangChain4jUserMessageToEmbabel() {
        // Given
        String content = "What is the weather today?";
        dev.langchain4j.data.message.UserMessage lc4jMsg = new dev.langchain4j.data.message.UserMessage(content);

        // When
        com.embabel.chat.Message embabelMsg = converter.toEmbabel(lc4jMsg);

        // Then
        assertThat(embabelMsg).isInstanceOf(com.embabel.chat.UserMessage.class);
        com.embabel.chat.UserMessage userMsg = (com.embabel.chat.UserMessage) embabelMsg;
        assertThat(userMsg.getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("Should preserve content in round-trip conversion (Embabel → LangChain4j → Embabel)")
    void shouldPreserveContentInRoundTripConversionEmbabelFirst() {
        // Given
        String originalContent = "This is a test message with special characters: !@#$%^&*()";
        com.embabel.chat.UserMessage originalMsg = new com.embabel.chat.UserMessage(originalContent);

        // When
        ChatMessage lc4jMsg = converter.toLangChain4j(originalMsg);
        com.embabel.chat.Message convertedBack = converter.toEmbabel(lc4jMsg);

        // Then
        assertThat(convertedBack).isInstanceOf(com.embabel.chat.UserMessage.class);
        com.embabel.chat.UserMessage finalMsg = (com.embabel.chat.UserMessage) convertedBack;
        assertThat(finalMsg.getContent()).isEqualTo(originalContent);
    }

    @Test
    @DisplayName("Should preserve content in round-trip conversion (LangChain4j → Embabel → LangChain4j)")
    void shouldPreserveContentInRoundTripConversionLangChain4jFirst() {
        // Given
        String originalContent = "Another test with unicode: 你好世界 🌍";
        dev.langchain4j.data.message.UserMessage originalMsg = new dev.langchain4j.data.message.UserMessage(
                originalContent);

        // When
        com.embabel.chat.Message embabelMsg = converter.toEmbabel(originalMsg);
        ChatMessage convertedBack = converter.toLangChain4j(embabelMsg);

        // Then
        assertThat(convertedBack).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        dev.langchain4j.data.message.UserMessage finalMsg = (dev.langchain4j.data.message.UserMessage) convertedBack;
        assertThat(finalMsg.singleText()).isEqualTo(originalContent);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when converting null Embabel message")
    void shouldThrowExceptionForNullEmbabelMessage() {
        assertThatThrownBy(() -> converter.toLangChain4j(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Embabel message cannot be null");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when converting null LangChain4j message")
    void shouldThrowExceptionForNullLangChain4jMessage() {
        assertThatThrownBy(() -> converter.toEmbabel(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LangChain4j message cannot be null");
    }

    // SystemMessage Tests

    @Test
    @DisplayName("Should convert Embabel SystemMessage to LangChain4j SystemMessage")
    void shouldConvertEmbabelSystemMessageToLangChain4j() {
        // Given
        String content = "You are a helpful assistant.";
        com.embabel.chat.SystemMessage embabelMsg = new com.embabel.chat.SystemMessage(content);

        // When
        ChatMessage lc4jMsg = converter.toLangChain4j(embabelMsg);

        // Then
        assertThat(lc4jMsg).isInstanceOf(dev.langchain4j.data.message.SystemMessage.class);
        dev.langchain4j.data.message.SystemMessage systemMsg = (dev.langchain4j.data.message.SystemMessage) lc4jMsg;
        assertThat(systemMsg.text()).isEqualTo(content);
    }

    @Test
    @DisplayName("Should convert LangChain4j SystemMessage to Embabel SystemMessage")
    void shouldConvertLangChain4jSystemMessageToEmbabel() {
        // Given
        String content = "You are an expert programmer.";
        dev.langchain4j.data.message.SystemMessage lc4jMsg = new dev.langchain4j.data.message.SystemMessage(content);

        // When
        com.embabel.chat.Message embabelMsg = converter.toEmbabel(lc4jMsg);

        // Then
        assertThat(embabelMsg).isInstanceOf(com.embabel.chat.SystemMessage.class);
        com.embabel.chat.SystemMessage systemMsg = (com.embabel.chat.SystemMessage) embabelMsg;
        assertThat(systemMsg.getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("Should preserve SystemMessage content in round-trip conversion")
    void shouldPreserveSystemMessageInRoundTrip() {
        // Given
        String originalContent = "System: Be concise and accurate.";
        com.embabel.chat.SystemMessage originalMsg = new com.embabel.chat.SystemMessage(originalContent);

        // When
        ChatMessage lc4jMsg = converter.toLangChain4j(originalMsg);
        com.embabel.chat.Message convertedBack = converter.toEmbabel(lc4jMsg);

        // Then
        assertThat(convertedBack).isInstanceOf(com.embabel.chat.SystemMessage.class);
        assertThat(((com.embabel.chat.SystemMessage) convertedBack).getContent()).isEqualTo(originalContent);
    }

    // AssistantMessage Tests

    @Test
    @DisplayName("Should convert Embabel AssistantMessage to LangChain4j AiMessage")
    void shouldConvertEmbabelAssistantMessageToLangChain4j() {
        // Given
        String content = "I can help you with that.";
        com.embabel.chat.AssistantMessage embabelMsg = new com.embabel.chat.AssistantMessage(content);

        // When
        ChatMessage lc4jMsg = converter.toLangChain4j(embabelMsg);

        // Then
        assertThat(lc4jMsg).isInstanceOf(dev.langchain4j.data.message.AiMessage.class);
        dev.langchain4j.data.message.AiMessage aiMsg = (dev.langchain4j.data.message.AiMessage) lc4jMsg;
        assertThat(aiMsg.text()).isEqualTo(content);
    }

    @Test
    @DisplayName("Should convert LangChain4j AiMessage to Embabel AssistantMessage")
    void shouldConvertLangChain4jAiMessageToEmbabel() {
        // Given
        String content = "Here is the answer to your question.";
        dev.langchain4j.data.message.AiMessage lc4jMsg = new dev.langchain4j.data.message.AiMessage(content);

        // When
        com.embabel.chat.Message embabelMsg = converter.toEmbabel(lc4jMsg);

        // Then
        assertThat(embabelMsg).isInstanceOf(com.embabel.chat.AssistantMessage.class);
        com.embabel.chat.AssistantMessage assistantMsg = (com.embabel.chat.AssistantMessage) embabelMsg;
        assertThat(assistantMsg.getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("Should preserve AssistantMessage content in round-trip conversion")
    void shouldPreserveAssistantMessageInRoundTrip() {
        // Given
        String originalContent = "The weather is sunny today.";
        com.embabel.chat.AssistantMessage originalMsg = new com.embabel.chat.AssistantMessage(originalContent);

        // When
        ChatMessage lc4jMsg = converter.toLangChain4j(originalMsg);
        com.embabel.chat.Message convertedBack = converter.toEmbabel(lc4jMsg);

        // Then
        assertThat(convertedBack).isInstanceOf(com.embabel.chat.AssistantMessage.class);
        assertThat(((com.embabel.chat.AssistantMessage) convertedBack).getContent()).isEqualTo(originalContent);
    }

    // ToolResultMessage Tests

    @Test
    @DisplayName("Should convert Embabel ToolResultMessage to LangChain4j ToolExecutionResultMessage")
    void shouldConvertEmbabelToolResultMessageToLangChain4j() {
        // Given
        String toolCallId = "call_123";
        String toolName = "get_weather";
        String content = "{\"temperature\": 72, \"condition\": \"sunny\"}";
        com.embabel.chat.ToolResultMessage embabelMsg = new com.embabel.chat.ToolResultMessage(
                toolCallId, toolName, content);

        // When
        ChatMessage lc4jMsg = converter.toLangChain4j(embabelMsg);

        // Then
        assertThat(lc4jMsg).isInstanceOf(dev.langchain4j.data.message.ToolExecutionResultMessage.class);
        dev.langchain4j.data.message.ToolExecutionResultMessage toolMsg = (dev.langchain4j.data.message.ToolExecutionResultMessage) lc4jMsg;
        assertThat(toolMsg.id()).isEqualTo(toolCallId);
        assertThat(toolMsg.toolName()).isEqualTo(toolName);
        assertThat(toolMsg.text()).isEqualTo(content);
    }

    @Test
    @DisplayName("Should convert LangChain4j ToolExecutionResultMessage to Embabel ToolResultMessage")
    void shouldConvertLangChain4jToolExecutionResultMessageToEmbabel() {
        // Given
        String toolCallId = "call_456";
        String toolName = "calculate";
        String content = "42";
        dev.langchain4j.data.message.ToolExecutionResultMessage lc4jMsg = new dev.langchain4j.data.message.ToolExecutionResultMessage(
                toolCallId, toolName, content);

        // When
        com.embabel.chat.Message embabelMsg = converter.toEmbabel(lc4jMsg);

        // Then
        assertThat(embabelMsg).isInstanceOf(com.embabel.chat.ToolResultMessage.class);
        com.embabel.chat.ToolResultMessage toolMsg = (com.embabel.chat.ToolResultMessage) embabelMsg;
        assertThat(toolMsg.getToolCallId()).isEqualTo(toolCallId);
        assertThat(toolMsg.getToolName()).isEqualTo(toolName);
        assertThat(toolMsg.getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("Should preserve ToolResultMessage in round-trip conversion")
    void shouldPreserveToolResultMessageInRoundTrip() {
        // Given
        String toolCallId = "call_789";
        String toolName = "search";
        String content = "Found 5 results";
        com.embabel.chat.ToolResultMessage originalMsg = new com.embabel.chat.ToolResultMessage(
                toolCallId, toolName, content);

        // When
        ChatMessage lc4jMsg = converter.toLangChain4j(originalMsg);
        com.embabel.chat.Message convertedBack = converter.toEmbabel(lc4jMsg);

        // Then
        assertThat(convertedBack).isInstanceOf(com.embabel.chat.ToolResultMessage.class);
        com.embabel.chat.ToolResultMessage finalMsg = (com.embabel.chat.ToolResultMessage) convertedBack;
        assertThat(finalMsg.getToolCallId()).isEqualTo(toolCallId);
        assertThat(finalMsg.getToolName()).isEqualTo(toolName);
        assertThat(finalMsg.getContent()).isEqualTo(content);
    }

    // AssistantMessageWithToolCalls Tests

    @Test
    @DisplayName("Should convert Embabel AssistantMessageWithToolCalls to LangChain4j AiMessage with tool requests")
    void shouldConvertEmbabelAssistantMessageWithToolCallsToLangChain4j() {
        // Given
        String content = "I'll check the weather for you.";
        List<com.embabel.chat.ToolCall> toolCalls = Arrays.asList(
                new com.embabel.chat.ToolCall("call_1", "get_weather", "{\"location\":\"London\"}"),
                new com.embabel.chat.ToolCall("call_2", "get_weather", "{\"location\":\"Paris\"}"));
        com.embabel.chat.AssistantMessageWithToolCalls embabelMsg = new com.embabel.chat.AssistantMessageWithToolCalls(
                content, toolCalls);

        // When
        ChatMessage lc4jMsg = converter.toLangChain4j(embabelMsg);

        // Then
        assertThat(lc4jMsg).isInstanceOf(dev.langchain4j.data.message.AiMessage.class);
        dev.langchain4j.data.message.AiMessage aiMsg = (dev.langchain4j.data.message.AiMessage) lc4jMsg;
        assertThat(aiMsg.text()).isEqualTo(content);
        assertThat(aiMsg.hasToolExecutionRequests()).isTrue();
        assertThat(aiMsg.toolExecutionRequests()).hasSize(2);
        assertThat(aiMsg.toolExecutionRequests().get(0).id()).isEqualTo("call_1");
        assertThat(aiMsg.toolExecutionRequests().get(0).name()).isEqualTo("get_weather");
        assertThat(aiMsg.toolExecutionRequests().get(1).id()).isEqualTo("call_2");
    }

    @Test
    @DisplayName("Should convert Embabel AssistantMessageWithToolCalls with empty content")
    void shouldConvertEmbabelAssistantMessageWithToolCallsEmptyContent() {
        // Given - empty content is valid for tool-only responses
        List<com.embabel.chat.ToolCall> toolCalls = Collections.singletonList(
                new com.embabel.chat.ToolCall("call_1", "calculate", "{\"expression\":\"2+2\"}"));
        com.embabel.chat.AssistantMessageWithToolCalls embabelMsg = new com.embabel.chat.AssistantMessageWithToolCalls(
                "", toolCalls);

        // When
        ChatMessage lc4jMsg = converter.toLangChain4j(embabelMsg);

        // Then
        assertThat(lc4jMsg).isInstanceOf(dev.langchain4j.data.message.AiMessage.class);
        dev.langchain4j.data.message.AiMessage aiMsg = (dev.langchain4j.data.message.AiMessage) lc4jMsg;
        assertThat(aiMsg.hasToolExecutionRequests()).isTrue();
        assertThat(aiMsg.toolExecutionRequests()).hasSize(1);
    }

    @Test
    @DisplayName("Should convert LangChain4j AiMessage with tool requests to Embabel AssistantMessageWithToolCalls")
    void shouldConvertLangChain4jAiMessageWithToolRequestsToEmbabel() {
        // Given
        String content = "Let me search for that information.";
        dev.langchain4j.agent.tool.ToolExecutionRequest request1 = dev.langchain4j.agent.tool.ToolExecutionRequest
                .builder()
                .id("call_search_1")
                .name("web_search")
                .arguments("{\"query\":\"Quarkus framework\"}")
                .build();
        dev.langchain4j.data.message.AiMessage lc4jMsg = new dev.langchain4j.data.message.AiMessage(content,
                Collections.singletonList(request1));

        // When
        com.embabel.chat.Message embabelMsg = converter.toEmbabel(lc4jMsg);

        // Then
        assertThat(embabelMsg).isInstanceOf(com.embabel.chat.AssistantMessageWithToolCalls.class);
        com.embabel.chat.AssistantMessageWithToolCalls assistantWithTools = (com.embabel.chat.AssistantMessageWithToolCalls) embabelMsg;
        assertThat(assistantWithTools.getContent()).isEqualTo(content);
        assertThat(assistantWithTools.getToolCalls()).hasSize(1);
        assertThat(assistantWithTools.getToolCalls().get(0).getId()).isEqualTo("call_search_1");
        assertThat(assistantWithTools.getToolCalls().get(0).getName()).isEqualTo("web_search");
        assertThat(assistantWithTools.getToolCalls().get(0).getArguments()).isEqualTo("{\"query\":\"Quarkus framework\"}");
    }

    @Test
    @DisplayName("Should preserve AssistantMessageWithToolCalls in round-trip conversion")
    void shouldPreserveAssistantMessageWithToolCallsInRoundTrip() {
        // Given
        String originalContent = "I'll help you with that.";
        List<com.embabel.chat.ToolCall> originalToolCalls = Arrays.asList(
                new com.embabel.chat.ToolCall("call_a", "tool_a", "{\"param\":\"value_a\"}"),
                new com.embabel.chat.ToolCall("call_b", "tool_b", "{\"param\":\"value_b\"}"));
        com.embabel.chat.AssistantMessageWithToolCalls originalMsg = new com.embabel.chat.AssistantMessageWithToolCalls(
                originalContent, originalToolCalls);

        // When
        ChatMessage lc4jMsg = converter.toLangChain4j(originalMsg);
        com.embabel.chat.Message convertedBack = converter.toEmbabel(lc4jMsg);

        // Then
        assertThat(convertedBack).isInstanceOf(com.embabel.chat.AssistantMessageWithToolCalls.class);
        com.embabel.chat.AssistantMessageWithToolCalls finalMsg = (com.embabel.chat.AssistantMessageWithToolCalls) convertedBack;
        assertThat(finalMsg.getContent()).isEqualTo(originalContent);
        assertThat(finalMsg.getToolCalls()).hasSize(2);
        assertThat(finalMsg.getToolCalls().get(0).getId()).isEqualTo("call_a");
        assertThat(finalMsg.getToolCalls().get(1).getId()).isEqualTo("call_b");
    }

}