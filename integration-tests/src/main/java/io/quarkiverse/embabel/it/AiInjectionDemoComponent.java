package io.quarkiverse.embabel.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.api.common.ExecutingOperationContext;
import com.embabel.common.ai.model.LlmOptions;

/**
 * Component demonstrating CDI injection of Ai-related beans.
 * <p>
 * This component verifies that the AiBeansProducer correctly provides:
 * <ul>
 * <li>{@link Ai} - Direct access to AI operations</li>
 * <li>{@link ExecutingOperationContext} - Full operation context</li>
 * <li>{@link AiBuilder} - Builder for creating custom Ai instances</li>
 * </ul>
 * <p>
 * All three beans are @Dependent scoped (new instance per injection point),
 * mirroring Spring Boot's prototype-scoped beans from InfrastructureInjectionConfiguration.
 */
@ApplicationScoped
public class AiInjectionDemoComponent {

    private static final Logger LOG = Logger.getLogger(AiInjectionDemoComponent.class);

    private final Ai ai;
    private final ExecutingOperationContext executingOperationContext;
    private final AiBuilder aiBuilder;

    /**
     * Constructor injection of all three Ai-related beans.
     * This verifies that the beans are properly registered and injectable.
     */
    @Inject
    public AiInjectionDemoComponent(
            Ai ai,
            ExecutingOperationContext executingOperationContext,
            AiBuilder aiBuilder) {
        this.ai = ai;
        this.executingOperationContext = executingOperationContext;
        this.aiBuilder = aiBuilder;
        LOG.info("AiInjectionDemoComponent created with injected Ai beans");
    }

    /**
     * Generate text using the injected Ai instance.
     */
    public String generateWithInjectedAi(String prompt) {
        return ai.withLlm(LlmOptions.withAutoLlm())
                .generateText(prompt);
    }

    /**
     * Generate text using Ai from the ExecutingOperationContext.
     */
    public String generateWithContextAi(String prompt) {
        Ai contextAi = executingOperationContext.ai();
        return contextAi.withLlm(LlmOptions.withAutoLlm())
                .generateText(prompt);
    }

    /**
     * Generate text using a custom Ai built with AiBuilder.
     */
    public String generateWithBuiltAi(String prompt, boolean showPrompts) {
        Ai builtAi = aiBuilder
                .withShowPrompts(showPrompts)
                .withShowLlmResponses(false)
                .ai();
        return builtAi.withLlm(LlmOptions.withAutoLlm())
                .generateText(prompt);
    }

    /**
     * Verify that all three beans were successfully injected.
     */
    public boolean areBeansInjected() {
        return ai != null && executingOperationContext != null && aiBuilder != null;
    }

    /**
     * Get information about the injected beans.
     */
    public String getBeanInfo() {
        return String.format("""
                Ai injected: %s
                ExecutingOperationContext injected: %s
                AiBuilder injected: %s
                AiBuilder showPrompts: %s
                AiBuilder showLlmResponses: %s
                """,
                ai != null,
                executingOperationContext != null,
                aiBuilder != null,
                aiBuilder != null ? aiBuilder.getShowPrompts() : "N/A",
                aiBuilder != null ? aiBuilder.getShowLlmResponses() : "N/A");
    }
}