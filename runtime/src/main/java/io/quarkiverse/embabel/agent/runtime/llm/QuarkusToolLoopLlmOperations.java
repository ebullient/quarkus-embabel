package io.quarkiverse.embabel.agent.runtime.llm;

import jakarta.validation.Validator;

import com.embabel.agent.api.common.Asyncer;
import com.embabel.agent.core.support.LlmInteraction;
import com.embabel.agent.spi.AutoLlmSelectionCriteriaResolver;
import com.embabel.agent.spi.ToolDecorator;
import com.embabel.agent.spi.loop.ToolLoopFactory;
import com.embabel.agent.spi.support.LlmDataBindingProperties;
import com.embabel.agent.spi.support.LlmOperationsPromptsProperties;
import com.embabel.agent.spi.support.OutputConverter;
import com.embabel.agent.spi.support.ToolLoopLlmOperations;
import com.embabel.agent.spi.validation.ValidationPromptGenerator;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.textio.template.TemplateRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.observation.ObservationRegistry;

/**
 * Quarkus-specific implementation of {@link ToolLoopLlmOperations}.
 * <p>
 * This class extends the base {@link ToolLoopLlmOperations} to provide Quarkus-specific
 * implementations of abstract methods, particularly {@link #createOutputConverter(Class, LlmInteraction)}
 * which uses Jackson for JSON conversion.
 * <p>
 * <b>Why This Class Exists</b>:
 * The base class {@link ToolLoopLlmOperations} provides a default implementation of
 * {@code createOutputConverter()} that returns {@code null}, expecting subclasses to override it.
 * Without this override, agent execution fails with {@link NullPointerException} when trying to
 * parse LLM responses into typed objects (any non-String output type).
 * <p>
 * <b>Architecture</b>:
 *
 * <pre>
 * ToolLoopLlmOperations (abstract base)
 *         ↓
 * QuarkusToolLoopLlmOperations (this class)
 *         ↓
 * createOutputConverter() → QuarkusOutputConverter (Jackson-based)
 * </pre>
 * <p>
 * <b>Comparison with Spring AI</b>:
 * Spring AI has {@code ChatClientLlmOperations} that extends {@code ToolLoopLlmOperations}
 * and provides Spring AI-specific output conversion. This class serves the same purpose
 * for Quarkus, using Jackson instead of Spring AI's converters.
 *
 * @see ToolLoopLlmOperations
 * @see QuarkusOutputConverter
 * @see com.embabel.agent.spi.support.springai.ChatClientLlmOperations
 */
public class QuarkusToolLoopLlmOperations extends ToolLoopLlmOperations {

    private final ObjectMapper objectMapper;

    /**
     * Creates a new Quarkus-specific LLM operations instance.
     * <p>
     * All parameters are passed through to the base class constructor.
     *
     * @param modelProvider provides access to configured LLM models
     * @param toolDecorator decorates tool calls with metadata
     * @param validator validates LLM outputs
     * @param validationPromptGenerator generates validation prompts
     * @param dataBindingProperties configures data binding behavior
     * @param autoLlmSelectionCriteriaResolver resolves LLM selection criteria
     * @param promptsProperties configures prompt behavior
     * @param objectMapper JSON object mapper (used by output converter)
     * @param observationRegistry observability registry
     * @param asyncer executes async operations
     * @param toolLoopFactory creates tool loop executors
     * @param templateRenderer renders prompt templates
     */
    public QuarkusToolLoopLlmOperations(
            ModelProvider modelProvider,
            ToolDecorator toolDecorator,
            Validator validator,
            ValidationPromptGenerator validationPromptGenerator,
            LlmDataBindingProperties dataBindingProperties,
            AutoLlmSelectionCriteriaResolver autoLlmSelectionCriteriaResolver,
            LlmOperationsPromptsProperties promptsProperties,
            ObjectMapper objectMapper,
            ObservationRegistry observationRegistry,
            Asyncer asyncer,
            ToolLoopFactory toolLoopFactory,
            TemplateRenderer templateRenderer) {
        super(
                modelProvider,
                toolDecorator,
                validator,
                validationPromptGenerator,
                dataBindingProperties,
                autoLlmSelectionCriteriaResolver,
                promptsProperties,
                objectMapper,
                observationRegistry,
                asyncer,
                toolLoopFactory,
                templateRenderer);
        this.objectMapper = objectMapper;
    }

    /**
     * Creates an output converter for parsing LLM responses into typed objects.
     * <p>
     * This method is called by the base class when the output type is not {@link String}.
     * It returns a {@link QuarkusOutputConverter} that uses Jackson to parse JSON responses.
     * <p>
     * <b>Critical Implementation Note</b>:
     * The base class {@link ToolLoopLlmOperations#createOutputConverter(Class, LlmInteraction)}
     * returns {@code null} by default. Without this override, the following code in the base class
     * will throw {@link NullPointerException}:
     *
     * <pre>
     * val outputParser: (String) -> O = if (outputClass == String::class.java) {
     *     { text -> sanitizeStringOutput(text) as O }
     * } else {
     *     { text -> converter!!.convert(text)!! }  // ← NPE here if converter is null
     * }
     * </pre>
     *
     * @param outputClass the target class to convert LLM responses to
     * @param interaction the LLM interaction context (currently unused, reserved for future use)
     * @param <O> the output type
     * @return a Jackson-based output converter
     */
    @Override
    protected <O> OutputConverter<O> createOutputConverter(
            Class<O> outputClass,
            LlmInteraction interaction) {
        return new QuarkusOutputConverter<>(outputClass, this.objectMapper);
    }
}