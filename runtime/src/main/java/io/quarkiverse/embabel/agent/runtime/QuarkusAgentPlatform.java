package io.quarkiverse.embabel.agent.runtime;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.inject.Instance;

import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.common.Asyncer;
import com.embabel.agent.api.common.PlatformServices;
import com.embabel.agent.api.common.autonomy.Autonomy;
import com.embabel.agent.api.event.AgenticEventListener;
import com.embabel.agent.api.event.observation.AgentInstrumentation;
import com.embabel.agent.api.event.observation.NoOpAgentInstrumentation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessRepository;
import com.embabel.agent.core.expression.LogicalExpressionParser;
import com.embabel.agent.core.internal.LlmOperations;
import com.embabel.agent.core.support.DefaultAgentPlatform;
import com.embabel.agent.spi.AgentProcessIdGenerator;
import com.embabel.agent.spi.BlackboardProvider;
import com.embabel.agent.spi.ContextRepository;
import com.embabel.agent.spi.OperationScheduler;
import com.embabel.agent.spi.ToolGroupResolver;
import com.embabel.agent.spi.config.spring.AgentPlatformProperties;
import com.embabel.agent.spi.expression.spel.SpelLogicalExpressionParser;
import com.embabel.chat.ConversationFactoryProvider;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.textio.template.TemplateRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.embabel.agent.runtime.service.ScopedPlatformServices;

/**
 * Quarkus-specific subclass of {@link DefaultAgentPlatform} that also implements
 * {@link PlatformServices}, making the platform itself the platform-services object.
 * <p>
 * This eliminates the circular-reference problem: {@link #getAgentPlatform()} simply
 * returns {@code this}. Structural fields are retained from the constructor so the
 * {@link PlatformServices} implementation is self-contained — it does not delegate
 * through {@code SpringContextPlatformServices}.
 * <p>
 * The CDI-resolved methods — {@link #autonomy()}, {@link #modelProvider()},
 * {@link #conversationFactoryProvider()}, and {@link #actionQosProperties()} —
 * resolve from CDI {@link Instance} fields rather than from a Spring
 * {@code ApplicationContext}.
 * <p>
 * {@link #withEventListener(AgenticEventListener)} returns a lightweight
 * {@link ScopedPlatformServices} wrapper that composes the extra listener while
 * still delegating CDI lookups back through {@code this}.
 */
public class QuarkusAgentPlatform extends DefaultAgentPlatform implements PlatformServices {

    private final LlmOperations llmOperations;
    private final AgenticEventListener eventListener;
    private final OperationScheduler operationScheduler;
    private final AgentProcessRepository agentProcessRepository;
    private final Asyncer asyncer;
    private final ObjectMapper objectMapper;
    private final OutputChannel outputChannel;
    private final TemplateRenderer templateRenderer;

    private final Instance<LogicalExpressionParser> lexpInstance;
    private final Instance<Autonomy> autonomyInstance;
    private final Instance<ModelProvider> modelProviderInstance;
    private final Instance<ConversationFactoryProvider> cfpInstance;
    private final AgentPlatformProperties platformProperties;
    private final Instance<AgentInstrumentation> agentInstrumentation;

    @SuppressWarnings("java:S107") // many params required to match DefaultAgentPlatform
    public QuarkusAgentPlatform(
            AgentPlatformProperties platformProperties,
            LlmOperations llmOperations,
            ToolGroupResolver toolGroupResolver,
            AgenticEventListener eventListener,
            AgentProcessIdGenerator agentProcessIdGenerator,
            ContextRepository contextRepository,
            AgentProcessRepository agentProcessRepository,
            OperationScheduler operationScheduler,
            BlackboardProvider blackboardProvider,
            Asyncer asyncer,
            ObjectMapper objectMapper,
            OutputChannel outputChannel,
            TemplateRenderer templateRenderer,
            Instance<LogicalExpressionParser> lexpInstance,
            Instance<Autonomy> autonomyInstance,
            Instance<ModelProvider> modelProviderInstance,
            Instance<ConversationFactoryProvider> cfpInstance,
            Instance<AgentInstrumentation> agentInstrumentation) {
        super(platformProperties.getName(), platformProperties.getDescription(), platformProperties.getProcessType(),
                llmOperations, toolGroupResolver, eventListener,
                agentProcessIdGenerator, contextRepository, agentProcessRepository,
                operationScheduler, blackboardProvider, asyncer,
                objectMapper, outputChannel, templateRenderer,
                null, null);

        this.llmOperations = llmOperations;
        this.eventListener = eventListener;
        this.operationScheduler = operationScheduler;
        this.agentProcessRepository = agentProcessRepository;
        this.asyncer = asyncer;
        this.objectMapper = objectMapper;
        this.outputChannel = outputChannel;
        this.templateRenderer = templateRenderer;

        this.lexpInstance = lexpInstance;
        this.autonomyInstance = autonomyInstance;
        this.modelProviderInstance = modelProviderInstance;
        this.cfpInstance = cfpInstance;
        this.platformProperties = platformProperties;
        this.agentInstrumentation = agentInstrumentation;
    }

    // ── AgentPlatform: return self as PlatformServices ────────────────────────

    @Override
    public PlatformServices getPlatformServices() {
        return this;
    }

    // ── PlatformServices ─────────────────────────────────────────────────────

    @Override
    public AgentPlatform getAgentPlatform() {
        return this;
    }

    @Override
    public LlmOperations getLlmOperations() {
        return llmOperations;
    }

    @Override
    public AgenticEventListener getEventListener() {
        return eventListener;
    }

    @Override
    public OperationScheduler getOperationScheduler() {
        return operationScheduler;
    }

    @Override
    public AgentProcessRepository getAgentProcessRepository() {
        return agentProcessRepository;
    }

    @Override
    public Asyncer getAsyncer() {
        return asyncer;
    }

    @Override
    public LogicalExpressionParser getLogicalExpressionParser() {
        List<LogicalExpressionParser> parsers = new ArrayList<>();
        boolean hasSpel = false;
        for (LogicalExpressionParser p : lexpInstance) {
            parsers.add(p);
            hasSpel |= p instanceof SpelLogicalExpressionParser;
        }
        if (!hasSpel) {
            parsers.add(new SpelLogicalExpressionParser());
        }
        return LogicalExpressionParser.Companion.of(parsers.toArray(new LogicalExpressionParser[0]));
    }

    @Override
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public OutputChannel getOutputChannel() {
        return outputChannel;
    }

    @Override
    public TemplateRenderer getTemplateRenderer() {
        return templateRenderer;
    }

    @Override
    public AgentInstrumentation getInstrumentation() {
        return agentInstrumentation.isUnsatisfied()
                ? NoOpAgentInstrumentation.INSTANCE
                : agentInstrumentation.get();
    }

    @Override
    public Autonomy autonomy() {
        return autonomyInstance.get();
    }

    @Override
    public ModelProvider modelProvider() {
        return modelProviderInstance.get();
    }

    @Override
    public ConversationFactoryProvider conversationFactoryProvider() {
        if (cfpInstance.isUnsatisfied()) {
            throw new UnsupportedOperationException(
                    "ConversationFactoryProvider is not available. " +
                            "Add embabel-chat-store to your dependencies.");
        }
        return cfpInstance.get();
    }

    @Override
    public AgentPlatformProperties.ActionQosProperties actionQosProperties() {
        return platformProperties.getActionQos();
    }

    // ── withEventListener: lightweight scoped wrapper ─────────────────────────

    @Override
    public PlatformServices withEventListener(AgenticEventListener agenticEventListener) {
        return new ScopedPlatformServices(this, agenticEventListener);
    }
}
