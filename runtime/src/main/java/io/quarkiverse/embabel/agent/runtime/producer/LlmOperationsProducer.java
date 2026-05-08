package io.quarkiverse.embabel.agent.runtime.producer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import com.embabel.agent.api.common.Asyncer;
import com.embabel.agent.core.internal.LlmOperations;
import com.embabel.agent.spi.AutoLlmSelectionCriteriaResolver;
import com.embabel.agent.spi.ToolDecorator;
import com.embabel.agent.spi.support.LlmDataBindingProperties;
import com.embabel.agent.spi.support.LlmOperationsPromptsProperties;
import com.embabel.agent.spi.support.ToolLoopLlmOperations;
import com.embabel.agent.spi.validation.DefaultValidationPromptGenerator;
import com.embabel.agent.spi.validation.ValidationPromptGenerator;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.textio.template.TemplateRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.observation.ObservationRegistry;
import io.quarkiverse.embabel.agent.runtime.llm.QuarkusToolLoopLlmOperations;
import io.quarkus.arc.DefaultBean;

/**
 * CDI producer for LLM operations beans.
 * <p>
 * This producer creates the core {@link LlmOperations} bean that handles all LLM interactions,
 * along with supporting beans like {@link Validator} and {@link ValidationPromptGenerator}.
 * <p>
 * The {@link LlmOperations} implementation uses {@link ToolLoopLlmOperations} which provides:
 * <ul>
 * <li>Tool loop execution with automatic retry and correction</li>
 * <li>Validation of LLM outputs</li>
 * <li>Template rendering for prompts</li>
 * <li>Data binding between LLM responses and Java objects</li>
 * </ul>
 */
@ApplicationScoped
public class LlmOperationsProducer {

    /**
     * Produces a {@link Validator} bean for validating LLM outputs.
     * <p>
     * Uses Jakarta Bean Validation's no-op validator. Applications can override
     * this by providing their own {@link Validator} bean.
     *
     * @return a default validator instance
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    public Validator validator() {
        // Return a no-op validator - applications can provide their own
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    /**
     * Produces a {@link ValidationPromptGenerator} for generating validation prompts.
     * <p>
     * Uses the default implementation that generates prompts for validation failures.
     * Applications can override this by providing their own {@link ValidationPromptGenerator} bean.
     *
     * @return a default validation prompt generator
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    public ValidationPromptGenerator validationPromptGenerator() {
        return new DefaultValidationPromptGenerator();
    }

    /**
     * Produces {@link LlmDataBindingProperties} for configuring data binding behavior.
     * <p>
     * Uses default properties. Applications can override by providing their own bean.
     *
     * @return default data binding properties
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    public LlmDataBindingProperties llmDataBindingProperties() {
        return new LlmDataBindingProperties();
    }

    /**
     * Produces {@link LlmOperationsPromptsProperties} for configuring prompt behavior.
     * <p>
     * Uses default properties. Applications can override by providing their own bean.
     *
     * @return default prompts properties
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    public LlmOperationsPromptsProperties llmOperationsPromptsProperties() {
        return new LlmOperationsPromptsProperties();
    }

    /**
     * Produces the main {@link LlmOperations} bean using {@link QuarkusToolLoopLlmOperations}.
     * <p>
     * This is the core bean that handles all LLM interactions in the agent platform.
     * It integrates:
     * <ul>
     * <li>{@link ModelProvider} - for selecting appropriate LLM models</li>
     * <li>{@link ToolDecorator} - for decorating tool calls with metadata</li>
     * <li>{@link Validator} - for validating LLM outputs</li>
     * <li>{@link TemplateRenderer} - for rendering prompt templates</li>
     * <li>{@link ObjectMapper} - for JSON serialization</li>
     * <li>{@link ObservationRegistry} - for observability (optional)</li>
     * <li>{@link Asyncer} - for async operations</li>
     * <li>{@link com.embabel.agent.spi.loop.ToolLoopFactory} - for tool loop execution (uses QuarkusToolLoopFactory)</li>
     * </ul>
     *
     * @param modelProvider provides access to configured LLM models
     * @param toolDecorator decorates tool calls with metadata
     * @param validator validates LLM outputs
     * @param validationPromptGenerator generates validation prompts
     * @param dataBindingProperties configures data binding behavior
     * @param promptsProperties configures prompt behavior
     * @param templateRenderer renders prompt templates
     * @param objectMapper JSON object mapper
     * @param observationRegistry optional observability registry
     * @param asyncer executes async operations
     * @param toolLoopFactory creates tool loop executors (injected QuarkusToolLoopFactory)
     * @param autoLlmSelectionCriteriaResolver resolves LLM selection criteria
     * @return configured LlmOperations instance
     */
    @Produces
    @ApplicationScoped
    public LlmOperations llmOperations(
            ModelProvider modelProvider,
            ToolDecorator toolDecorator,
            Validator validator,
            ValidationPromptGenerator validationPromptGenerator,
            LlmDataBindingProperties dataBindingProperties,
            LlmOperationsPromptsProperties promptsProperties,
            TemplateRenderer templateRenderer,
            @Named("embabelJacksonObjectMapper") ObjectMapper objectMapper,
            Instance<ObservationRegistry> observationRegistry,
            Asyncer asyncer,
            com.embabel.agent.spi.loop.ToolLoopFactory toolLoopFactory,
            AutoLlmSelectionCriteriaResolver autoLlmSelectionCriteriaResolver) {

        return new QuarkusToolLoopLlmOperations(
                modelProvider,
                toolDecorator,
                validator,
                validationPromptGenerator,
                dataBindingProperties,
                autoLlmSelectionCriteriaResolver,
                promptsProperties,
                objectMapper,
                observationRegistry.isUnsatisfied() ? ObservationRegistry.NOOP : observationRegistry.get(),
                asyncer,
                toolLoopFactory,
                templateRenderer);
    }
}