package io.quarkiverse.embabel.agent.runtime.loop;

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

import com.embabel.agent.spi.loop.ToolLoopResult;
import com.embabel.chat.Message;
import com.embabel.chat.SystemMessage;
import com.embabel.chat.UserMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.embabel.agent.runtime.message.MessageConverterImpl;
import io.quarkiverse.embabel.agent.runtime.tool.ToolSpecificationConverter;

/**
 * Unit tests for {@link QuarkusToolLoop}.
 */
class QuarkusToolLoopTest {

    private ChatModel mockChatModel;
    private MessageConverterImpl messageConverter; // Real converter, not mocked
    private ToolSpecificationConverter mockToolConverter;
    private QuarkusToolLoop toolLoop;

    @BeforeEach
    void setUp() {
        mockChatModel = mock(ChatModel.class);
        messageConverter = new MessageConverterImpl(); // Use real implementation
        mockToolConverter = mock(ToolSpecificationConverter.class);
        toolLoop = new QuarkusToolLoop(mockChatModel, messageConverter, mockToolConverter);
        toolLoop.maxIterations = 10;
    }

    @Test
    void shouldReturnResultWhenNoToolCallsRequested() {
        // Given
        List<Message> initialMessages = List.of(
                new SystemMessage("You are a helpful assistant"),
                new UserMessage("What is 2+2?"));

        // Mock LLM response without tool calls - converter will handle the conversion
        AiMessage aiMessage = AiMessage.from("The answer is 4");
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(aiMessage)
                .build();
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);

        // When
        ToolLoopResult<String> result = toolLoop.execute(
                initialMessages,
                Collections.emptyList(),
                text -> text);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getResult()).isEqualTo("The answer is 4");
        assertThat(result.getRawResponseText()).isEqualTo("The answer is 4");
        assertThat(result.getConversationHistory()).hasSize(3); // system + user + assistant
        assertThat(result.getTotalIterations()).isEqualTo(1);
        assertThat(result.getInjectedTools()).isEmpty();
        assertThat(result.getReplanRequested()).isFalse();

        // Verify LLM was called once
        verify(mockChatModel).chat(any(ChatRequest.class));
    }

    @Test
    void shouldDetectToolCallsAndThrowUnsupportedOperation() {
        // Given
        List<Message> initialMessages = List.of(
                new SystemMessage("You are a helpful assistant"),
                new UserMessage("What's the weather?"));

        // Mock LLM response WITH tool calls - converter will handle the conversion
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .id("call_123")
                .name("get_weather")
                .arguments("{\"location\":\"London\"}")
                .build();
        AiMessage aiMessage = AiMessage.from(toolRequest);
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(aiMessage)
                .build();
        when(mockChatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse);

        // When/Then - should detect tool calls and throw UnsupportedOperationException
        assertThatThrownBy(() -> toolLoop.execute(
                initialMessages,
                Collections.emptyList(),
                text -> text))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Tool execution not yet implemented");

        // Verify LLM was called
        verify(mockChatModel).chat(any(ChatRequest.class));
    }

}