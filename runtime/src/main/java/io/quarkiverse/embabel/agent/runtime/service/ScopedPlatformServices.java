package io.quarkiverse.embabel.agent.runtime.service;

import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.common.Asyncer;
import com.embabel.agent.api.common.PlatformServices;
import com.embabel.agent.api.common.autonomy.Autonomy;
import com.embabel.agent.api.event.AgenticEventListener;
import com.embabel.agent.api.event.observation.AgentInstrumentation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessRepository;
import com.embabel.agent.core.expression.LogicalExpressionParser;
import com.embabel.agent.core.internal.LlmOperations;
import com.embabel.agent.spi.OperationScheduler;
import com.embabel.agent.spi.config.spring.AgentPlatformProperties;
import com.embabel.chat.ConversationFactoryProvider;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.textio.template.TemplateRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A lightweight, CDI-free {@link PlatformServices} wrapper returned by
 * {@link QuarkusAgentPlatform#withEventListener(AgenticEventListener)}.
 * <p>
 * Each agent process gets its own scoped copy with an extra
 * {@link AgenticEventListener} layered on top of the platform-level one.
 * All other methods delegate directly to the parent {@link PlatformServices}
 * delegate (which is the {@link QuarkusAgentPlatform} itself), so CDI-resolved
 * beans like {@link Autonomy} and {@link ModelProvider} still resolve correctly
 * through the delegate even inside a process-scoped copy.
 * <p>
 * Nesting is handled by composing the extra listeners rather than mutating:
 * calling {@code withEventListener} on a scoped instance wraps the same
 * delegate with the combined listener.
 */
public class ScopedPlatformServices implements PlatformServices {

    private final PlatformServices delegate;
    private final AgenticEventListener extraListener;

    public ScopedPlatformServices(PlatformServices delegate, AgenticEventListener extra) {
        this.delegate = delegate;
        this.extraListener = extra;
    }

    // ── Structural properties: pure delegation ────────────────────────────────

    @Override
    public AgentPlatform getAgentPlatform() {
        return delegate.getAgentPlatform();
    }

    @Override
    public LlmOperations getLlmOperations() {
        return delegate.getLlmOperations();
    }

    @Override
    public OperationScheduler getOperationScheduler() {
        return delegate.getOperationScheduler();
    }

    @Override
    public AgentProcessRepository getAgentProcessRepository() {
        return delegate.getAgentProcessRepository();
    }

    @Override
    public Asyncer getAsyncer() {
        return delegate.getAsyncer();
    }

    @Override
    public LogicalExpressionParser getLogicalExpressionParser() {
        return delegate.getLogicalExpressionParser();
    }

    @Override
    public ObjectMapper getObjectMapper() {
        return delegate.getObjectMapper();
    }

    @Override
    public OutputChannel getOutputChannel() {
        return delegate.getOutputChannel();
    }

    @Override
    public TemplateRenderer getTemplateRenderer() {
        return delegate.getTemplateRenderer();
    }

    @Override
    public AgentInstrumentation getInstrumentation() {
        return delegate.getInstrumentation();
    }

    // ── eventListener: composed with the extra listener ───────────────────────

    @Override
    public AgenticEventListener getEventListener() {
        return AgenticEventListener.Companion.of(delegate.getEventListener(), extraListener);
    }

    // ── CDI-resolved beans: flow back through the delegate ────────────────────
    // delegate IS the QuarkusAgentPlatform, so CDI resolution still works.

    @Override
    public Autonomy autonomy() {
        return delegate.autonomy();
    }

    @Override
    public ModelProvider modelProvider() {
        return delegate.modelProvider();
    }

    @Override
    public ConversationFactoryProvider conversationFactoryProvider() {
        return delegate.conversationFactoryProvider();
    }

    @Override
    public AgentPlatformProperties.ActionQosProperties actionQosProperties() {
        return delegate.actionQosProperties();
    }

    // ── withEventListener: wrap rather than mutate ────────────────────────────

    @Override
    public PlatformServices withEventListener(AgenticEventListener agenticEventListener) {
        return new ScopedPlatformServices(delegate,
                AgenticEventListener.Companion.of(extraListener, agenticEventListener));
    }
}
