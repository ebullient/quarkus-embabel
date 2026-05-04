package io.quarkiverse.embabel.agent.runtime.loop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.api.tool.ToolCallContext;
import com.embabel.agent.api.tool.callback.ToolCallInspector;
import com.embabel.agent.api.tool.callback.ToolLoopInspector;
import com.embabel.agent.api.tool.callback.ToolLoopTransformer;
import com.embabel.agent.spi.loop.EmptyResponsePolicy;
import com.embabel.agent.spi.loop.LlmMessageResponse;
import com.embabel.agent.spi.loop.LlmMessageSender;
import com.embabel.agent.spi.loop.MaxIterationsExceededException;
import com.embabel.agent.spi.loop.ToolInjectionStrategy;
import com.embabel.agent.spi.loop.ToolLoop;
import com.embabel.agent.spi.loop.ToolLoopResult;
import com.embabel.agent.spi.loop.ToolNotFoundPolicy;
import com.embabel.chat.AssistantMessageWithToolCalls;
import com.embabel.chat.Message;
import com.embabel.chat.ToolCall;
import com.embabel.chat.ToolResultMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import kotlin.jvm.functions.Function1;

/**
 * Quarkus implementation of {@link ToolLoop} that executes tool-calling conversations.
 * <p>
 * This implementation is NOT a singleton - instances are created per request by
 * {@link QuarkusToolLoopFactory}. Each instance is bound to a specific {@link LlmMessageSender}
 * which encapsulates the model selection and message conversion logic.
 * <p>
 * This design enables multiple model support: different requests can use different models
 * by creating ToolLoop instances with different LlmMessageSender implementations.
 * <p>
 * The loop continues until:
 * <ul>
 * <li>The LLM provides a final response without tool calls</li>
 * <li>Maximum iterations are reached</li>
 * <li>An error occurs</li>
 * </ul>
 *
 * @see ToolLoop
 * @see QuarkusToolLoopFactory
 * @see LlmMessageSender
 */
public class QuarkusToolLoop implements ToolLoop {

    private final LlmMessageSender messageSender;
    private final ObjectMapper objectMapper;
    private final ToolInjectionStrategy injectionStrategy;
    private final int maxIterations;
    private final Function1<? super Tool, ? extends Tool> toolDecorator;
    private final List<? extends ToolLoopInspector> toolLoopInspectors;
    private final List<? extends ToolLoopTransformer> toolLoopTransformers;
    private final List<? extends ToolCallInspector> toolCallInspectors;
    private final ToolCallContext toolCallContext;
    private final ToolNotFoundPolicy toolNotFoundPolicy;
    private final EmptyResponsePolicy emptyResponsePolicy;

    /**
     * Package-private constructor - called by {@link QuarkusToolLoopFactory}.
     * <p>
     * All 11 parameters match the {@link com.embabel.agent.spi.loop.ToolLoopFactory#create}
     * signature, ensuring compatibility with the Embabel SPI.
     *
     * @param messageSender the LLM message sender (encapsulates model + converters)
     * @param objectMapper for JSON deserialization of tool results
     * @param injectionStrategy strategy for dynamic tool injection
     * @param maxIterations maximum loop iterations before throwing exception
     * @param toolDecorator optional decorator for injected tools
     * @param toolLoopInspectors read-only observers for tool loop lifecycle events
     * @param toolLoopTransformers transformers for modifying conversation history
     * @param toolCallInspectors read-only observers for individual tool call events
     * @param toolCallContext context propagated to tool invocations
     * @param toolNotFoundPolicy policy for handling tool-not-found errors
     * @param emptyResponsePolicy policy for handling empty LLM responses
     */
    QuarkusToolLoop(
            LlmMessageSender messageSender,
            ObjectMapper objectMapper,
            ToolInjectionStrategy injectionStrategy,
            int maxIterations,
            Function1<? super Tool, ? extends Tool> toolDecorator,
            List<? extends ToolLoopInspector> toolLoopInspectors,
            List<? extends ToolLoopTransformer> toolLoopTransformers,
            List<? extends ToolCallInspector> toolCallInspectors,
            ToolCallContext toolCallContext,
            ToolNotFoundPolicy toolNotFoundPolicy,
            EmptyResponsePolicy emptyResponsePolicy) {
        this.messageSender = messageSender;
        this.objectMapper = objectMapper;
        this.injectionStrategy = injectionStrategy;
        this.maxIterations = maxIterations;
        this.toolDecorator = toolDecorator;
        this.toolLoopInspectors = toolLoopInspectors;
        this.toolLoopTransformers = toolLoopTransformers;
        this.toolCallInspectors = toolCallInspectors;
        this.toolCallContext = toolCallContext;
        this.toolNotFoundPolicy = toolNotFoundPolicy;
        this.emptyResponsePolicy = emptyResponsePolicy;
    }

    /**
     * Execute a conversation with tool calling until completion.
     * <p>
     * This method implements the core tool loop logic:
     * <ol>
     * <li>Delegate to messageSender to call LLM with current messages and tools</li>
     * <li>If LLM requests tool calls, execute them and add results to conversation</li>
     * <li>Repeat until LLM provides final response or max iterations reached</li>
     * <li>Parse final response and return result with conversation history</li>
     * </ol>
     * <p>
     * Note: Message conversion is handled by the LlmMessageSender, not here.
     * This keeps the loop implementation clean and delegates format conversion
     * to the appropriate layer.
     *
     * @param initialMessages the starting messages (system + user)
     * @param initialTools the initially available tools
     * @param outputParser function to parse the final response to the output type
     * @param <O> the output type
     * @return the result containing parsed output and conversation history
     */
    @Override
    public <O> ToolLoopResult<O> execute(
            List<? extends Message> initialMessages,
            List<? extends Tool> initialTools,
            Function1<? super String, ? extends O> outputParser) {

        List<Message> messages = new ArrayList<>(initialMessages);
        List<Tool> tools = new ArrayList<>(initialTools);

        int iterations = 0;
        while (iterations < maxIterations) {
            iterations++;

            // Delegate to messageSender - it handles all message conversion
            LlmMessageResponse response = messageSender.call(messages, tools);
            Message embabelMessage = response.getMessage();

            // Add to history
            messages.add(embabelMessage);

            // Check if LLM requested tool calls
            if (!(embabelMessage instanceof AssistantMessageWithToolCalls)) {
                // No tools - parse and return
                String responseText = response.getTextContent() != null ? response.getTextContent() : "";
                O output = outputParser.invoke(responseText);
                return new ToolLoopResult<>(
                        output,
                        responseText,
                        messages,
                        iterations,
                        Collections.emptyList(), // injectedTools
                        Collections.emptyList(), // removedTools
                        response.getUsage(), // totalUsage
                        false, // replanRequested
                        null, // replanReason
                        blackboard -> {
                        }); // empty BlackboardUpdater
            }

            // Execute tools
            AssistantMessageWithToolCalls toolCallMessage = (AssistantMessageWithToolCalls) embabelMessage;
            for (ToolCall toolCall : toolCallMessage.getToolCalls()) {
                // Find tool by name
                Tool tool = findTool(tools, toolCall.getName());
                if (tool == null) {
                    // Handle tool not found - add error message
                    ToolResultMessage errorMsg = new ToolResultMessage(
                            toolCall.getId(),
                            toolCall.getName(),
                            "Error: Tool '" + toolCall.getName() + "' not found");
                    messages.add(errorMsg);
                    continue;
                }

                // Execute tool
                Tool.Result result = tool.call(toolCall.getArguments());

                // Convert result to string
                String resultText = resultToString(result);

                // Create ToolResultMessage
                ToolResultMessage resultMsg = new ToolResultMessage(
                        toolCall.getId(),
                        toolCall.getName(),
                        resultText);

                // Add to history
                messages.add(resultMsg);
            }
        }

        throw new MaxIterationsExceededException(maxIterations);
    }

    /**
     * Find a tool by name in the list of available tools.
     *
     * @param tools the list of tools to search
     * @param name the name of the tool to find
     * @return the tool if found, null otherwise
     */
    private Tool findTool(List<? extends Tool> tools, String name) {
        return tools.stream()
                .filter(tool -> tool.getDefinition().getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Convert a Tool.Result to a string representation.
     *
     * @param result the tool result to convert
     * @return string representation of the result
     */
    private String resultToString(Tool.Result result) {
        if (result instanceof Tool.Result.Text textResult) {
            return textResult.getContent();
        } else if (result instanceof Tool.Result.WithArtifact artifactResult) {
            return artifactResult.getContent();
        } else if (result instanceof Tool.Result.Error errorResult) {
            return "Error: " + errorResult.getMessage();
        }
        return result.toString();
    }
}