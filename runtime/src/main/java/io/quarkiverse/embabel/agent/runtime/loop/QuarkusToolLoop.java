package io.quarkiverse.embabel.agent.runtime.loop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.spi.loop.MaxIterationsExceededException;
import com.embabel.agent.spi.loop.ToolLoop;
import com.embabel.agent.spi.loop.ToolLoopResult;
import com.embabel.chat.Message;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.embabel.agent.runtime.message.MessageConverter;
import io.quarkiverse.embabel.agent.runtime.tool.ToolSpecificationConverter;
import kotlin.jvm.functions.Function1;

/**
 * Quarkus implementation of {@link ToolLoop} that executes tool-calling conversations
 * using LangChain4j's {@link ChatModel}.
 * <p>
 * This implementation provides Embabel's tool loop functionality in Quarkus applications,
 * managing the conversation flow between the LLM and tool executions. It handles:
 * <ul>
 * <li>Message conversion between Embabel and LangChain4j formats</li>
 * <li>Tool specification conversion for LLM consumption</li>
 * <li>Iterative tool calling until completion or max iterations</li>
 * <li>Conversation history tracking</li>
 * </ul>
 * <p>
 * The loop continues until:
 * <ul>
 * <li>The LLM provides a final response without tool calls</li>
 * <li>Maximum iterations are reached</li>
 * <li>An error occurs</li>
 * </ul>
 *
 * @see ToolLoop
 * @see ChatModel
 */
@ApplicationScoped
public class QuarkusToolLoop implements ToolLoop {

    private final ChatModel model;
    private final MessageConverter messageConverter;
    private final ToolSpecificationConverter toolConverter;

    @ConfigProperty(name = "embabel.agent.platform.autonomy.max-iterations", defaultValue = "10")
    int maxIterations;

    /**
     * Constructor for CDI injection.
     *
     * @param model the ChatModel to use for LLM interactions
     * @param messageConverter converter for Embabel ↔ LangChain4j messages
     * @param toolConverter converter for Embabel tools → LangChain4j tool specifications
     */
    @Inject
    public QuarkusToolLoop(
            ChatModel model,
            MessageConverter messageConverter,
            ToolSpecificationConverter toolConverter) {
        this.model = model;
        this.messageConverter = messageConverter;
        this.toolConverter = toolConverter;
    }

    /**
     * Execute a conversation with tool calling until completion.
     * <p>
     * This method implements the core tool loop logic:
     * <ol>
     * <li>Convert initial messages to LangChain4j format</li>
     * <li>Send messages to LLM with available tools</li>
     * <li>If LLM requests tool calls, execute them and add results to conversation</li>
     * <li>Repeat until LLM provides final response or max iterations reached</li>
     * <li>Parse final response and return result with conversation history</li>
     * </ol>
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
        List<ToolSpecification> toolSpecs = initialTools.stream()
                .map(toolConverter::toLangChain4j)
                .collect(Collectors.toList());

        int iterations = 0;
        while (iterations < maxIterations) {
            iterations++;

            // Convert to LangChain4j
            List<ChatMessage> lc4jMessages = messages.stream()
                    .map(messageConverter::toLangChain4j)
                    .collect(Collectors.toList());

            // Build chat request with tools
            ChatRequest.Builder requestBuilder = ChatRequest.builder()
                    .messages(lc4jMessages);

            if (!toolSpecs.isEmpty()) {
                requestBuilder.toolSpecifications(toolSpecs);
            }

            // Call LLM (model from quarkus-langchain4j)
            ChatResponse response = model.chat(requestBuilder.build());
            AiMessage aiMessage = response.aiMessage();

            // Add to history
            messages.add(messageConverter.toEmbabel(aiMessage));

            // Check for tool calls
            if (!aiMessage.hasToolExecutionRequests()) {
                // No tools - parse and return
                String responseText = aiMessage.text() != null ? aiMessage.text() : "";
                O output = outputParser.invoke(responseText);
                return new ToolLoopResult<>(
                        output,
                        responseText,
                        messages,
                        iterations,
                        Collections.emptyList(), // injectedTools
                        Collections.emptyList(), // removedTools
                        null, // totalUsage
                        false, // replanRequested
                        null, // replanReason
                        blackboard -> {
                        }); // empty BlackboardUpdater
            }

            // TODO: Execute tools (Step 17)
            throw new UnsupportedOperationException("Tool execution not yet implemented");
        }

        throw new MaxIterationsExceededException(maxIterations);
    }
}