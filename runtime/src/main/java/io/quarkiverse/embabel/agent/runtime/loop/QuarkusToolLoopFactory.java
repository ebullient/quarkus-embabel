package io.quarkiverse.embabel.agent.runtime.loop;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.api.tool.ToolCallContext;
import com.embabel.agent.api.tool.callback.ToolCallInspector;
import com.embabel.agent.api.tool.callback.ToolLoopInspector;
import com.embabel.agent.api.tool.callback.ToolLoopTransformer;
import com.embabel.agent.spi.loop.EmptyResponsePolicy;
import com.embabel.agent.spi.loop.LlmMessageSender;
import com.embabel.agent.spi.loop.ToolInjectionStrategy;
import com.embabel.agent.spi.loop.ToolLoop;
import com.embabel.agent.spi.loop.ToolLoopFactory;
import com.embabel.agent.spi.loop.ToolNotFoundPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;

import kotlin.jvm.functions.Function1;

/**
 * Quarkus implementation of {@link ToolLoopFactory} that creates {@link QuarkusToolLoop} instances.
 * <p>
 * This factory is a singleton bean that creates ToolLoop instances per request. Each ToolLoop
 * instance is bound to a specific {@link LlmMessageSender}, which encapsulates the model selection
 * and message conversion logic.
 * <p>
 * This design enables multiple model support: different requests can use different models by
 * creating ToolLoop instances with different LlmMessageSender implementations.
 * <p>
 * The factory implements the Embabel SPI {@link ToolLoopFactory} interface, ensuring compatibility
 * with the Embabel agent framework.
 *
 * @see ToolLoopFactory
 * @see QuarkusToolLoop
 * @see LlmMessageSender
 */
@ApplicationScoped
public class QuarkusToolLoopFactory implements ToolLoopFactory {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Create a {@link ToolLoop} instance with the provided configuration.
     * <p>
     * This method implements the Embabel SPI {@link ToolLoopFactory#create} interface,
     * accepting all 11 parameters defined by the SPI and passing them to the
     * {@link QuarkusToolLoop} constructor.
     * <p>
     * The {@code llmMessageSender} parameter is particularly important as it encapsulates
     * the model selection - different senders can use different models, enabling
     * multi-model support.
     * <p>
     * Note: In Phase 5 (v1.0), only the core parameters are actively used:
     * <ul>
     * <li>{@code llmMessageSender} - makes LLM calls with the correct model</li>
     * <li>{@code objectMapper} - deserializes tool results</li>
     * <li>{@code maxIterations} - loop termination</li>
     * </ul>
     * The remaining parameters are passed through for future phases but not yet utilized.
     *
     * @param llmMessageSender the LLM message sender (encapsulates model + converters)
     * @param objectMapper for JSON deserialization of tool results
     * @param injectionStrategy strategy for dynamic tool injection (Phase 6)
     * @param maxIterations maximum loop iterations before throwing exception
     * @param toolDecorator optional decorator for injected tools (Phase 6)
     * @param toolLoopInspectors read-only observers for tool loop lifecycle events (Phase 7)
     * @param toolLoopTransformers transformers for modifying conversation history (Phase 7)
     * @param toolCallInspectors read-only observers for individual tool call events (Phase 7)
     * @param toolCallContext context propagated to tool invocations (Phase 6)
     * @param toolNotFoundPolicy policy for handling tool-not-found errors (Phase 6)
     * @param emptyResponsePolicy policy for handling empty LLM responses (Phase 6)
     * @return a new ToolLoop instance configured with the provided parameters
     */
    @Override
    public ToolLoop create(
            LlmMessageSender llmMessageSender,
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

        // Create ToolLoop instance with all 11 parameters
        // The llmMessageSender already has the correct model selected
        return new QuarkusToolLoop(
                llmMessageSender,
                objectMapper,
                injectionStrategy,
                maxIterations,
                toolDecorator,
                toolLoopInspectors,
                toolLoopTransformers,
                toolCallInspectors,
                toolCallContext,
                toolNotFoundPolicy,
                emptyResponsePolicy);
    }
}
