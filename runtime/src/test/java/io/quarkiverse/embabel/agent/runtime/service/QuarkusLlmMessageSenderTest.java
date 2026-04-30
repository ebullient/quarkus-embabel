package io.quarkiverse.embabel.agent.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.Usage;
import com.embabel.agent.spi.loop.LlmMessageResponse;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.Message;
import com.embabel.chat.UserMessage;
import com.embabel.common.ai.model.LlmOptions;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.embabel.agent.runtime.message.MessageConverter;
import io.quarkiverse.embabel.agent.runtime.tool.ToolSpecificationConverter;

/**
 * Unit tests for {@link QuarkusLlmMessageSender}.
 */
class QuarkusLlmMessageSenderTest {

    private ChatModel mockChatModel;
    private LlmOptions options;
    private MessageConverter mockMessageConverter;
    private ToolSpecificationConverter mockToolConverter;
    private QuarkusLlmMessageSender sender;

    @BeforeEach
    void setUp() {
        mockChatModel = mock(ChatModel.class);
        options = LlmOptions.withDefaults();
        mockMessageConverter = mock(MessageConverter.class);
        mockToolConverter = mock(ToolSpecificationConverter.class);
        sender = new QuarkusLlmMessageSender(mockChatModel, options, mockMessageConverter, mockToolConverter);
    }

    @Test
    void shouldThrowExceptionWhenChatModelIsNull() {
        assertThatThrownBy(() -> new QuarkusLlmMessageSender(null, options, mockMessageConverter, mockToolConverter))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ChatModel cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenOptionsIsNull() {
        assertThatThrownBy(() -> new QuarkusLlmMessageSender(mockChatModel, null, mockMessageConverter, mockToolConverter))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("LlmOptions cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenMessageConverterIsNull() {
        assertThatThrownBy(() -> new QuarkusLlmMessageSender(mockChatModel, options, null, mockToolConverter))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("MessageConverter cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenToolConverterIsNull() {
        assertThatThrownBy(() -> new QuarkusLlmMessageSender(mockChatModel, options, mockMessageConverter, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ToolSpecificationConverter cannot be null");
    }

    @Test
    void shouldCallLlmWithoutTools() {
        // Given
        UserMessage userMessage = new UserMessage("Hello");
        List<Message> messages = List.of(userMessage);
        List<Tool> tools = Collections.emptyList();

        dev.langchain4j.data.message.UserMessage lc4jUserMessage = new dev.langchain4j.data.message.UserMessage("Hello");
        AiMessage lc4jAiMessage = new AiMessage("Hi there!");
        AssistantMessage embabelResponse = new AssistantMessage("Hi there!");

        TokenUsage tokenUsage = new TokenUsage(10, 5);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .tokenUsage(tokenUsage)
                .finishReason(FinishReason.STOP)
                .build();
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(lc4jAiMessage)
                .metadata(metadata)
                .build();

        when(mockMessageConverter.toLangChain4j(userMessage)).thenReturn(lc4jUserMessage);
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);
        when(mockMessageConverter.toEmbabel(lc4jAiMessage)).thenReturn(embabelResponse);

        // When
        LlmMessageResponse response = sender.call(messages, tools);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo(embabelResponse);
        assertThat(response.getTextContent()).isEqualTo("Hi there!");
        assertThat(response.getUsage()).isNotNull();
        assertThat(response.getUsage().getPromptTokens()).isEqualTo(10);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(5);

        // Verify ChatRequest was built without tools
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(mockChatModel).chat(requestCaptor.capture());
        ChatRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.toolSpecifications()).isEmpty();
    }

    @Test
    void shouldCallLlmWithTools() {
        // Given
        UserMessage userMessage = new UserMessage("What's the weather?");
        List<Message> messages = List.of(userMessage);

        Tool mockTool = mock(Tool.class);
        Tool.Definition mockDefinition = mock(Tool.Definition.class);
        when(mockTool.getDefinition()).thenReturn(mockDefinition);
        when(mockDefinition.getName()).thenReturn("get_weather");
        List<Tool> tools = List.of(mockTool);

        dev.langchain4j.data.message.UserMessage lc4jUserMessage = new dev.langchain4j.data.message.UserMessage(
                "What's the weather?");
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("get_weather")
                .description("Get weather information")
                .build();

        // AI responds with a tool call
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("call_123")
                .name("get_weather")
                .arguments("{\"location\":\"San Francisco\"}")
                .build();
        AiMessage lc4jAiMessage = AiMessage.from(toolRequest);

        com.embabel.chat.AssistantMessageWithToolCalls embabelResponse = mock(
                com.embabel.chat.AssistantMessageWithToolCalls.class);

        TokenUsage tokenUsage = new TokenUsage(15, 8);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .tokenUsage(tokenUsage)
                .finishReason(FinishReason.TOOL_EXECUTION)
                .build();
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(lc4jAiMessage)
                .metadata(metadata)
                .build();

        when(mockMessageConverter.toLangChain4j(userMessage)).thenReturn(lc4jUserMessage);
        when(mockToolConverter.toLangChain4j(mockTool)).thenReturn(toolSpec);
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);
        when(mockMessageConverter.toEmbabel(lc4jAiMessage)).thenReturn(embabelResponse);

        // When
        LlmMessageResponse response = sender.call(messages, tools);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo(embabelResponse);
        assertThat(response.getUsage()).isNotNull();
        assertThat(response.getUsage().getPromptTokens()).isEqualTo(15);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(8);

        // Verify tool converter was called
        verify(mockToolConverter).toLangChain4j(mockTool);

        // Verify ChatRequest was built with tools
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(mockChatModel).chat(requestCaptor.capture());
        ChatRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.toolSpecifications()).hasSize(1);
        assertThat(capturedRequest.toolSpecifications().get(0).name()).isEqualTo("get_weather");
    }

    @Test
    void shouldCallLlmWithMultipleTools() {
        // Given
        UserMessage userMessage = new UserMessage("Help me with tasks");
        List<Message> messages = List.of(userMessage);

        Tool mockTool1 = mock(Tool.class);
        Tool.Definition mockDefinition1 = mock(Tool.Definition.class);
        when(mockTool1.getDefinition()).thenReturn(mockDefinition1);
        when(mockDefinition1.getName()).thenReturn("get_weather");

        Tool mockTool2 = mock(Tool.class);
        Tool.Definition mockDefinition2 = mock(Tool.Definition.class);
        when(mockTool2.getDefinition()).thenReturn(mockDefinition2);
        when(mockDefinition2.getName()).thenReturn("search_web");

        List<Tool> tools = List.of(mockTool1, mockTool2);

        dev.langchain4j.data.message.UserMessage lc4jUserMessage = new dev.langchain4j.data.message.UserMessage(
                "Help me with tasks");

        ToolSpecification toolSpec1 = ToolSpecification.builder()
                .name("get_weather")
                .description("Get weather")
                .build();
        ToolSpecification toolSpec2 = ToolSpecification.builder()
                .name("search_web")
                .description("Search the web")
                .build();

        AiMessage lc4jAiMessage = new AiMessage("I can help with that");
        AssistantMessage embabelResponse = new AssistantMessage("I can help with that");

        TokenUsage tokenUsage = new TokenUsage(20, 10);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .tokenUsage(tokenUsage)
                .finishReason(FinishReason.STOP)
                .build();
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(lc4jAiMessage)
                .metadata(metadata)
                .build();

        when(mockMessageConverter.toLangChain4j(userMessage)).thenReturn(lc4jUserMessage);
        when(mockToolConverter.toLangChain4j(mockTool1)).thenReturn(toolSpec1);
        when(mockToolConverter.toLangChain4j(mockTool2)).thenReturn(toolSpec2);
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);
        when(mockMessageConverter.toEmbabel(lc4jAiMessage)).thenReturn(embabelResponse);

        // When
        LlmMessageResponse response = sender.call(messages, tools);

        // Then
        assertThat(response).isNotNull();
        verify(mockToolConverter).toLangChain4j(mockTool1);
        verify(mockToolConverter).toLangChain4j(mockTool2);

        // Verify ChatRequest has both tools
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(mockChatModel).chat(requestCaptor.capture());
        ChatRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.toolSpecifications()).hasSize(2);
        assertThat(capturedRequest.toolSpecifications().get(0).name()).isEqualTo("get_weather");
        assertThat(capturedRequest.toolSpecifications().get(1).name()).isEqualTo("search_web");
    }

    @Test
    void shouldHandleNullTokenUsage() {
        // Given
        UserMessage userMessage = new UserMessage("Hello");
        List<Message> messages = List.of(userMessage);
        List<Tool> tools = Collections.emptyList();

        dev.langchain4j.data.message.UserMessage lc4jUserMessage = new dev.langchain4j.data.message.UserMessage("Hello");
        AiMessage lc4jAiMessage = new AiMessage("Hi!");
        AssistantMessage embabelResponse = new AssistantMessage("Hi!");

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .tokenUsage(null)
                .finishReason(FinishReason.STOP)
                .build();
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(lc4jAiMessage)
                .metadata(metadata)
                .build();

        when(mockMessageConverter.toLangChain4j(userMessage)).thenReturn(lc4jUserMessage);
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);
        when(mockMessageConverter.toEmbabel(lc4jAiMessage)).thenReturn(embabelResponse);

        // When
        LlmMessageResponse response = sender.call(messages, tools);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getUsage()).isNull();
    }

    @Test
    void shouldHandleNullTextInAiMessage() {
        // Given
        UserMessage userMessage = new UserMessage("Execute tool");
        List<Message> messages = List.of(userMessage);

        Tool mockTool = mock(Tool.class);
        Tool.Definition mockDefinition = mock(Tool.Definition.class);
        when(mockTool.getDefinition()).thenReturn(mockDefinition);
        when(mockDefinition.getName()).thenReturn("execute");
        List<Tool> tools = List.of(mockTool);

        dev.langchain4j.data.message.UserMessage lc4jUserMessage = new dev.langchain4j.data.message.UserMessage(
                "Execute tool");
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("execute")
                .description("Execute something")
                .build();

        // AI responds with only tool call, no text
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("call_456")
                .name("execute")
                .arguments("{}")
                .build();
        AiMessage lc4jAiMessage = AiMessage.from(toolRequest);

        com.embabel.chat.AssistantMessageWithToolCalls embabelResponse = mock(
                com.embabel.chat.AssistantMessageWithToolCalls.class);

        TokenUsage tokenUsage = new TokenUsage(12, 6);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .tokenUsage(tokenUsage)
                .finishReason(FinishReason.TOOL_EXECUTION)
                .build();
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(lc4jAiMessage)
                .metadata(metadata)
                .build();

        when(mockMessageConverter.toLangChain4j(userMessage)).thenReturn(lc4jUserMessage);
        when(mockToolConverter.toLangChain4j(mockTool)).thenReturn(toolSpec);
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);
        when(mockMessageConverter.toEmbabel(lc4jAiMessage)).thenReturn(embabelResponse);

        // When
        LlmMessageResponse response = sender.call(messages, tools);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTextContent()).isEmpty(); // Text is empty string when only tool calls
        assertThat(response.getMessage()).isEqualTo(embabelResponse);
    }

    @Test
    void shouldThrowExceptionWhenMessagesIsNull() {
        assertThatThrownBy(() -> sender.call(null, Collections.emptyList()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Messages cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenToolsIsNull() {
        List<Message> messages = List.of(new UserMessage("Hello"));
        assertThatThrownBy(() -> sender.call(messages, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Tools cannot be null");
    }

    @Test
    void shouldConvertTokenUsageCorrectly() {
        // Given
        UserMessage userMessage = new UserMessage("Test");
        List<Message> messages = List.of(userMessage);
        List<Tool> tools = Collections.emptyList();

        dev.langchain4j.data.message.UserMessage lc4jUserMessage = new dev.langchain4j.data.message.UserMessage("Test");
        AiMessage lc4jAiMessage = new AiMessage("Response");
        AssistantMessage embabelResponse = new AssistantMessage("Response");

        TokenUsage tokenUsage = new TokenUsage(100, 50);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .tokenUsage(tokenUsage)
                .finishReason(FinishReason.STOP)
                .build();
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(lc4jAiMessage)
                .metadata(metadata)
                .build();

        when(mockMessageConverter.toLangChain4j(userMessage)).thenReturn(lc4jUserMessage);
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);
        when(mockMessageConverter.toEmbabel(lc4jAiMessage)).thenReturn(embabelResponse);

        // When
        LlmMessageResponse response = sender.call(messages, tools);

        // Then
        Usage usage = response.getUsage();
        assertThat(usage).isNotNull();
        assertThat(usage.getPromptTokens()).isEqualTo(100);
        assertThat(usage.getCompletionTokens()).isEqualTo(50);
        assertThat(usage.getTotalTokens()).isEqualTo(150);
    }
}