package io.quarkiverse.embabel.agent.runtime.producer;

import java.util.concurrent.Executors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import com.embabel.agent.api.channel.DevNullOutputChannel;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.common.Asyncer;
import com.embabel.agent.api.common.ranking.Ranker;
import com.embabel.agent.core.AgentProcessRepository;
import com.embabel.agent.core.internal.LlmOperations;
import com.embabel.agent.core.support.InMemoryBlackboardProvider;
import com.embabel.agent.spi.AgentProcessIdGenerator;
import com.embabel.agent.spi.AutoLlmSelectionCriteriaResolver;
import com.embabel.agent.spi.BlackboardProvider;
import com.embabel.agent.spi.ContextRepository;
import com.embabel.agent.spi.OperationScheduler;
import com.embabel.agent.spi.config.spring.ContextRepositoryProperties;
import com.embabel.agent.spi.config.spring.ProcessRepositoryProperties;
import com.embabel.agent.spi.logging.ColorPalette;
import com.embabel.agent.spi.logging.DefaultColorPalette;
import com.embabel.agent.spi.support.ExecutorAsyncer;
import com.embabel.agent.spi.support.InMemoryAgentProcessRepository;
import com.embabel.agent.spi.support.InMemoryContextRepository;
import com.embabel.agent.spi.support.LlmRanker;
import com.embabel.agent.spi.support.ProcessOptionsOperationScheduler;
import com.embabel.agent.spi.support.RankingProperties;
import com.embabel.common.core.NameGenerator;
import com.embabel.common.textio.template.JinjavaTemplateRenderer;
import com.embabel.common.textio.template.TemplateRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.quarkus.arc.DefaultBean;

/**
 * CDI producer for core Embabel Agent platform beans.
 * <p>
 * This producer creates the fundamental dependencies required by {@link com.embabel.agent.core.support.DefaultAgentPlatform},
 * following the pattern established by {@link io.quarkiverse.embabel.agent.runtime.provider.QuarkusModelProvider}.
 * <p>
 * The beans produced here include:
 * <ul>
 * <li>Name generation for process IDs</li>
 * <li>Template rendering for agent prompts</li>
 * <li>Color palettes for terminal output</li>
 * <li>Jackson ObjectMapper for JSON serialization</li>
 * <li>Repositories for process and context storage</li>
 * <li>Operation scheduling</li>
 * <li>Output channels</li>
 * <li>LLM selection criteria</li>
 * <li>Ranking services</li>
 * </ul>
 *
 * @see com.embabel.agent.core.support.DefaultAgentPlatform
 * @see com.embabel.agent.spi.config.spring.AgentPlatformConfiguration
 */
@ApplicationScoped
public class CoreBeansProducer {

    /**
     * Produces a {@link NameGenerator} for generating process IDs.
     * Uses Moby name generator (Docker-style names like "happy_einstein").
     * Note: MobyNameGenerator is a Kotlin top-level val, accessed as a static field.
     *
     * @return the name generator instance
     */
    @Produces
    @ApplicationScoped
    public NameGenerator nameGenerator() {
        // MobyNameGenerator is a Kotlin top-level val - access via NameGeneratorKt.getMobyNameGenerator()
        return com.embabel.common.core.NameGeneratorKt.getMobyNameGenerator();
    }

    /**
     * Produces a {@link TemplateRenderer} for rendering Jinjava templates.
     * Used for agent prompt templates and other text generation.
     *
     * @return the template renderer instance
     */
    @Produces
    @ApplicationScoped
    public TemplateRenderer templateRenderer() {
        return new JinjavaTemplateRenderer();
    }

    /**
     * Produces a default {@link ColorPalette} for terminal output.
     * Can be overridden by user-provided beans.
     *
     * @return the default color palette
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    public ColorPalette colorPalette() {
        return new DefaultColorPalette();
    }

    /**
     * Produces a named {@link ObjectMapper} for Embabel JSON serialization.
     * Configured with JavaTimeModule and ISO-8601 date formatting.
     *
     * @return the configured ObjectMapper
     */
    @Produces
    @ApplicationScoped
    @Named("embabelJacksonObjectMapper")
    public ObjectMapper embabelJacksonObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Produces an {@link AgentProcessRepository} for storing agent processes.
     * Uses in-memory storage with default properties.
     *
     * @return the agent process repository
     */
    @Produces
    @ApplicationScoped
    public AgentProcessRepository agentProcessRepository() {
        return new InMemoryAgentProcessRepository(
                new ProcessRepositoryProperties());
    }

    /**
     * Produces a {@link ContextRepository} for storing agent contexts.
     * Uses in-memory storage with default properties.
     *
     * @return the context repository
     */
    @Produces
    @ApplicationScoped
    public ContextRepository contextRepository() {
        return new InMemoryContextRepository(
                new ContextRepositoryProperties());
    }

    /**
     * Produces an {@link OperationScheduler} for scheduling agent operations.
     * Uses the default process options scheduler.
     *
     * @return the operation scheduler
     */
    @Produces
    @ApplicationScoped
    public OperationScheduler operationScheduler() {
        return new ProcessOptionsOperationScheduler();
    }

    /**
     * Produces a default {@link OutputChannel} for agent output.
     * Uses DevNull channel by default (no output).
     * Can be overridden by user-provided beans.
     * Note: DevNullOutputChannel is a Kotlin object, accessed as INSTANCE.
     *
     * @return the default output channel
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    public OutputChannel outputChannel() {
        // DevNullOutputChannel is a Kotlin object - access via INSTANCE field
        return DevNullOutputChannel.INSTANCE;
    }

    /**
     * Produces an {@link AutoLlmSelectionCriteriaResolver} for LLM selection.
     * Uses the default resolver implementation.
     * Note: DEFAULT is a Kotlin companion object field.
     *
     * @return the LLM selection criteria resolver
     */
    @Produces
    @ApplicationScoped
    public AutoLlmSelectionCriteriaResolver autoLlmSelectionCriteriaResolver() {
        // Access Kotlin companion object field via getDEFAULT()
        return AutoLlmSelectionCriteriaResolver.Companion.getDEFAULT();
    }

    /**
     * Produces {@link RankingProperties} for configuring the ranker.
     * Uses default properties.
     *
     * @return the ranking properties
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    public RankingProperties rankingProperties() {
        return new RankingProperties();
    }

    /**
     * Produces a {@link Ranker} for LLM-based ranking operations.
     * Requires {@link LlmOperations} and {@link RankingProperties}.
     *
     * @param llmOperations the LLM operations service
     * @param rankingProperties the ranking configuration properties
     * @return the ranker instance
     */
    @Produces
    @ApplicationScoped
    public Ranker ranker(
            LlmOperations llmOperations,
            RankingProperties rankingProperties) {
        return new LlmRanker(llmOperations, rankingProperties);
    }

    /**
     * Produces an {@link AgentProcessIdGenerator} for generating process IDs.
     * Uses the RANDOM generator which creates UUID-based process IDs.
     * Note: RANDOM is a Kotlin companion object field.
     *
     * @return the agent process ID generator
     */
    @Produces
    @ApplicationScoped
    public AgentProcessIdGenerator agentProcessIdGenerator() {
        // Access Kotlin companion object field via Companion.getRANDOM()
        return AgentProcessIdGenerator.Companion.getRANDOM();
    }

    /**
     * Produces a {@link BlackboardProvider} for managing agent blackboards.
     * Uses the in-memory provider for storing agent state.
     * Note: InMemoryBlackboardProvider is a Kotlin object.
     *
     * @return the blackboard provider
     */
    @Produces
    @ApplicationScoped
    public BlackboardProvider blackboardProvider() {
        // InMemoryBlackboardProvider is a Kotlin object - access via INSTANCE
        return InMemoryBlackboardProvider.INSTANCE;
    }

    /**
     * Produces an {@link Asyncer} for asynchronous operations.
     * Uses ExecutorAsyncer with virtual threads for efficient async execution.
     *
     * @return the asyncer instance
     */
    @Produces
    @ApplicationScoped
    public Asyncer asyncer() {
        // Use ExecutorAsyncer with virtual thread executor
        return new ExecutorAsyncer(
                Executors.newVirtualThreadPerTaskExecutor());
    }
}