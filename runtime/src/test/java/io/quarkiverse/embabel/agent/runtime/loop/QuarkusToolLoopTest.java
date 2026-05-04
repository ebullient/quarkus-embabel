package io.quarkiverse.embabel.agent.runtime.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.api.tool.ToolCallContext;
import com.embabel.agent.spi.loop.EmptyResponsePolicy;
import com.embabel.agent.spi.loop.LlmMessageResponse;
import com.embabel.agent.spi.loop.LlmMessageSender;
import com.embabel.agent.spi.loop.ToolInjectionStrategy;
import com.embabel.agent.spi.loop.ToolLoopResult;
import com.embabel.agent.spi.loop.ToolNotFoundPolicy;
import com.embabel.chat.AssistantMessage;
import com.embabel.chat.AssistantMessageWithToolCalls;
import com.embabel.chat.Message;
import com.embabel.chat.SystemMessage;
import com.embabel.chat.ToolCall;
import com.embabel.chat.ToolResultMessage;
import com.embabel.chat.UserMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link QuarkusToolLoop} using the factory pattern.
 */
class QuarkusToolLoopTest {

    private LlmMessageSender mockMessageSender;
    private ObjectMapper objectMapper;
    private QuarkusToolLoopFactory factory;
    private QuarkusToolLoop toolLoop;

    @BeforeEach
    void setUp() {
        mockMessageSender = mock(LlmMessageSender.class);
        objectMapper = new ObjectMapper();
        factory = new QuarkusToolLoopFactory();
        factory.objectMapper = objectMapper;

        // Create ToolLoop via factory with minimal parameters
        toolLoop = (QuarkusToolLoop) factory.create(
                mockMessageSender,
                objectMapper,
                ToolInjectionStrategy.Companion.getNONE(),
                10,
                null, // toolDecorator
                Collections.emptyList(), // toolLoopInspectors
                Collections.emptyList(), // toolLoopTransformers
                Collections.emptyList(), // toolCallInspectors
                ToolCallContext.EMPTY,
                mock(ToolNotFoundPolicy.class),
                mock(EmptyResponsePolicy.class));
    }

    @Test
    void shouldReturnResultWhenNoToolCallsRequested() {
        // Given
        List<Message> initialMessages = List.of(
                new SystemMessage("You are a helpful assistant"),
                new UserMessage("What is 2+2?"));

        // Mock LLM response without tool calls
        AssistantMessage assistantMessage = new AssistantMessage("The answer is 4");
        LlmMessageResponse response = new LlmMessageResponse(
                assistantMessage,
                "The answer is 4",
                null);
        when(mockMessageSender.call(anyList(), anyList())).thenReturn(response);

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

        // Verify message sender was called once
        verify(mockMessageSender).call(anyList(), anyList());
    }

    @Test
    void shouldExecuteToolsAndReturnFinalResponse() {
        // Given
        List<Message> initialMessages = List.of(
                new SystemMessage("You are a helpful assistant"),
                new UserMessage("What's the weather in London?"));

        // Create a mock tool
        Tool weatherTool = Tool.create(
                "get_weather",
                "Get weather for a location",
                input -> Tool.Result.text("Sunny, 22°C"));

        // First call: LLM requests tool
        ToolCall toolCall = new ToolCall(
                "call_123",
                "get_weather",
                "{\"location\":\"London\"}");
        AssistantMessageWithToolCalls toolCallMessage = new AssistantMessageWithToolCalls(
                List.of(toolCall));
        LlmMessageResponse toolCallResponse = new LlmMessageResponse(
                toolCallMessage,
                "",
                null);

        // Second call: LLM provides final answer after tool result
        AssistantMessage finalMessage = new AssistantMessage("The weather in London is sunny with 22°C");
        LlmMessageResponse finalResponse = new LlmMessageResponse(
                finalMessage,
                "The weather in London is sunny with 22°C",
                null);

        when(mockMessageSender.call(anyList(), anyList()))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);

        // When
        ToolLoopResult<String> result = toolLoop.execute(
                initialMessages,
                List.of(weatherTool),
                text -> text);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getResult()).isEqualTo("The weather in London is sunny with 22°C");
        assertThat(result.getTotalIterations()).isEqualTo(2);
        assertThat(result.getConversationHistory()).hasSize(5); // system + user + assistant(tool call) + tool result + final

        // Verify tool result message was added
        Message toolResultMsg = result.getConversationHistory().get(3);
        assertThat(toolResultMsg).isInstanceOf(ToolResultMessage.class);
        ToolResultMessage toolResult = (ToolResultMessage) toolResultMsg;
        assertThat(toolResult.getToolCallId()).isEqualTo("call_123");
        assertThat(toolResult.getToolName()).isEqualTo("get_weather");
        assertThat(toolResult.getContent()).isEqualTo("Sunny, 22°C");

        // Verify message sender was called twice
        verify(mockMessageSender, times(2)).call(anyList(), anyList());
    }

    @Test
    void shouldHandleToolErrors() {
        // Given
        List<Message> initialMessages = List.of(
                new SystemMessage("You are a helpful assistant"),
                new UserMessage("What's the weather?"));

        // LLM requests a tool that doesn't exist
        ToolCall toolCall = new ToolCall(
                "call_123",
                "nonexistent_tool",
                "{}");
        AssistantMessageWithToolCalls toolCallMessage = new AssistantMessageWithToolCalls(
                List.of(toolCall));
        LlmMessageResponse toolCallResponse = new LlmMessageResponse(
                toolCallMessage,
                "",
                null);

        // Second call: LLM handles the error
        AssistantMessage finalMessage = new AssistantMessage("I don't have access to that tool");
        LlmMessageResponse finalResponse = new LlmMessageResponse(
                finalMessage,
                "I don't have access to that tool",
                null);

        when(mockMessageSender.call(anyList(), anyList()))
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);

        // When
        ToolLoopResult<String> result = toolLoop.execute(
                initialMessages,
                Collections.emptyList(),
                text -> text);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalIterations()).isEqualTo(2);

        // Verify error message was added
        Message toolResultMsg = result.getConversationHistory().get(3);
        assertThat(toolResultMsg).isInstanceOf(ToolResultMessage.class);
        ToolResultMessage toolResult = (ToolResultMessage) toolResultMsg;
        assertThat(toolResult.getContent()).contains("Error: Tool 'nonexistent_tool' not found");

        verify(mockMessageSender, times(2)).call(anyList(), anyList());
    }

}