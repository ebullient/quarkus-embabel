package io.quarkiverse.embabel.agent.runtime.producer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.common.Asyncer;
import com.embabel.agent.api.common.autonomy.Autonomy;
import com.embabel.agent.api.event.observation.AgentInstrumentation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessRepository;
import com.embabel.agent.core.expression.LogicalExpressionParser;
import com.embabel.agent.core.internal.LlmOperations;
import com.embabel.agent.spi.AgentProcessIdGenerator;
import com.embabel.agent.spi.BlackboardProvider;
import com.embabel.agent.spi.ContextRepository;
import com.embabel.agent.spi.OperationScheduler;
import com.embabel.agent.spi.ToolGroupResolver;
import com.embabel.agent.spi.config.spring.AgentPlatformProperties;
import com.embabel.chat.ConversationFactoryProvider;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.textio.template.TemplateRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.embabel.agent.runtime.QuarkusAgentPlatform;

/**
 * CDI producer for the {@link AgentPlatform} bean.
 * <p>
 * Produces a {@link QuarkusAgentPlatform} which extends {@link com.embabel.agent.core.support.DefaultAgentPlatform}
 * and also implements {@link com.embabel.agent.api.common.PlatformServices}, so the platform
 * is its own platform-services object. CDI {@link Instance} fields for
 * {@link Autonomy}, {@link ModelProvider}, and {@link ConversationFactoryProvider} are
 * injected and passed to the platform, which resolves them lazily on demand.
 * <p>
 * <b>Configuration</b>:
 * The platform can be configured via application.properties:
 * <ul>
 * <li>{@code embabel.agent.platform.name} - Platform name (default: "quarkus-agent-platform")</li>
 * <li>{@code embabel.agent.platform.description} - Platform description</li>
 * <li>{@code embabel.agent.platform.process-type} - Process type: SIMPLE or CONCURRENT</li>
 * </ul>
 *
 * @see QuarkusAgentPlatform
 * @see com.embabel.agent.spi.config.spring.AgentPlatformConfiguration
 */
@ApplicationScoped
public class AgentPlatformProducer {

    @Inject
    AgentPlatformProperties platformProperties;

    @Inject
    LlmOperations llmOperations;

    @Inject
    ToolGroupResolver toolGroupResolver;

    @Inject
    AggregatedEventListener eventListener;

    @Inject
    AgentProcessIdGenerator agentProcessIdGenerator;

    @Inject
    ContextRepository contextRepository;

    @Inject
    AgentProcessRepository agentProcessRepository;

    @Inject
    OperationScheduler operationScheduler;

    @Inject
    BlackboardProvider blackboardProvider;

    @Inject
    Asyncer asyncer;

    @Inject
    @Named("embabelJacksonObjectMapper")
    ObjectMapper objectMapper;

    @Inject
    OutputChannel outputChannel;

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    Instance<LogicalExpressionParser> lexpInstance;

    @Inject
    Instance<Autonomy> autonomyInstance;

    @Inject
    Instance<ModelProvider> modelProviderInstance;

    @Inject
    Instance<ConversationFactoryProvider> cfpInstance;

    @Inject
    Instance<AgentInstrumentation> agentInstrumentation;

    /**
     * Produces the {@link AgentPlatform} bean as a {@link QuarkusAgentPlatform}.
     * <p>
     * {@link QuarkusAgentPlatform} extends {@link com.embabel.agent.core.support.DefaultAgentPlatform}
     * and implements {@link com.embabel.agent.api.common.PlatformServices}, so
     * {@code agentPlatform.getPlatformServices()} returns {@code this}.
     * CDI {@link Instance} fields for {@link Autonomy}, {@link ModelProvider}, and
     * {@link ConversationFactoryProvider} are resolved lazily on demand.
     *
     * @return the configured Quarkus agent platform instance
     */
    @Produces
    @ApplicationScoped
    public AgentPlatform agentPlatform() {
        return new QuarkusAgentPlatform(
                platformProperties,
                llmOperations,
                toolGroupResolver,
                eventListener,
                agentProcessIdGenerator,
                contextRepository,
                agentProcessRepository,
                operationScheduler,
                blackboardProvider,
                asyncer,
                objectMapper,
                outputChannel,
                templateRenderer,
                lexpInstance,
                autonomyInstance,
                modelProviderInstance,
                cfpInstance,
                agentInstrumentation);
    }
}
