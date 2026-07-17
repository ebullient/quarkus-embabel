package io.quarkiverse.embabel.agent.runtime.producer;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import com.embabel.agent.api.common.autonomy.Autonomy;
import com.embabel.agent.api.common.autonomy.AutonomyProperties;
import com.embabel.agent.api.common.ranking.Ranker;
import com.embabel.agent.api.tool.config.ToolLoopConfiguration;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.spi.config.spring.AgentPlatformProperties;
import com.embabel.agent.spi.config.spring.AgentPlatformProperties.ActionQosProperties.ActionProperties;
import com.embabel.agent.spi.support.RankingProperties;
import com.embabel.common.ai.model.ConfigurableModelProviderProperties;

import io.smallrye.config.SmallRyeConfig;

/**
 * CDI producer for Embabel configuration beans.
 * <p>
 * This producer creates CDI beans from Embabel's Kotlin {@code @ConfigurationProperties} classes
 * by manually binding application properties. This is necessary because:
 * <ul>
 * <li>Embabel config classes are Kotlin data classes (immutable, no setters)</li>
 * <li>{@code quarkus-spring-boot-properties} doesn't automatically create CDI beans</li>
 * <li>Manual property binding gives us full control over construction</li>
 * </ul>
 * <p>
 * Consolidates all Embabel configuration production in one place, following the pattern
 * established in {@link ActionQosProducer}.
 *
 * @see ToolLoopConfiguration
 * @see ConfigurableModelProviderProperties
 * @see ActionQosProducer
 */
@ApplicationScoped
public class EmbabelConfigProducer {

    private static final Logger log = Logger.getLogger(EmbabelConfigProducer.class);

    private static final String PLATFORM_PREFIX = "embabel.agent.platform.";
    private static final String RANKING_PREFIX = PLATFORM_PREFIX + "ranking.";
    private static final String TOOLLOOP_PREFIX = PLATFORM_PREFIX + "toolloop.";
    private static final String AUTONOMY_PREFIX = PLATFORM_PREFIX + "autonomy.";
    private static final String MODELS_PREFIX = "embabel.models.";

    private final AgentPlatformProperties platformProperties;
    private final String rankingPropertyPrefix;

    public EmbabelConfigProducer() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        AgentPlatformProperties props = new AgentPlatformProperties();

        // Top-level (Quarkus-specific defaults override upstream "embabel-default")
        props.setName(config.getOptionalValue(PLATFORM_PREFIX + "name", String.class)
                .orElse("quarkus-agent-platform"));
        props.setDescription(config.getOptionalValue(PLATFORM_PREFIX + "description", String.class)
                .orElse("Quarkus Agent Platform"));
        config.getOptionalValue(PLATFORM_PREFIX + "process-type", AgentPlatformProperties.ProcessType.class)
                .ifPresent(props::setProcessType);

        // Scanning
        AgentPlatformProperties.ScanningConfig scanning = props.getScanning();
        config.getOptionalValue(PLATFORM_PREFIX + "scanning.annotation", Boolean.class)
                .ifPresent(scanning::setAnnotation);
        config.getOptionalValue(PLATFORM_PREFIX + "scanning.bean", Boolean.class)
                .ifPresent(scanning::setBean);

        // Ranking
        AgentPlatformProperties.RankingConfig ranking = props.getRanking();
        config.getOptionalValue(RANKING_PREFIX + "llm", String.class)
                .ifPresent(ranking::setLlm);
        config.getOptionalValue(RANKING_PREFIX + "max-attempts", Integer.class)
                .ifPresent(ranking::setMaxAttempts);
        config.getOptionalValue(RANKING_PREFIX + "backoff-millis", Long.class)
                .ifPresent(ranking::setBackoffMillis);
        config.getOptionalValue(RANKING_PREFIX + "backoff-multiplier", Double.class)
                .ifPresent(ranking::setBackoffMultiplier);
        config.getOptionalValue(RANKING_PREFIX + "backoff-max-interval", Long.class)
                .ifPresent(ranking::setBackoffMaxInterval);
        this.rankingPropertyPrefix = config
                .getOptionalValue(RANKING_PREFIX + "property-prefix", String.class)
                .orElse(RankingProperties.PREFIX);

        // LLM Operations
        AgentPlatformProperties.LlmOperationsConfig llmOps = props.getLlmOperations();
        config.getOptionalValue(PLATFORM_PREFIX + "llm-operations.prompts.maybe-prompt-template", String.class)
                .ifPresent(llmOps.getPrompts()::setMaybePromptTemplate);
        config.getOptionalValue(PLATFORM_PREFIX + "llm-operations.prompts.generate-examples-by-default", Boolean.class)
                .ifPresent(llmOps.getPrompts()::setGenerateExamplesByDefault);
        config.getOptionalValue(PLATFORM_PREFIX + "llm-operations.data-binding.max-attempts", Integer.class)
                .ifPresent(llmOps.getDataBinding()::setMaxAttempts);
        config.getOptionalValue(PLATFORM_PREFIX + "llm-operations.data-binding.fixed-backoff-millis", Long.class)
                .ifPresent(llmOps.getDataBinding()::setFixedBackoffMillis);

        // Process ID Generation
        AgentPlatformProperties.ProcessIdGenerationConfig pidGen = props.getProcessIdGeneration();
        config.getOptionalValue(PLATFORM_PREFIX + "process-id-generation.include-version", Boolean.class)
                .ifPresent(pidGen::setIncludeVersion);
        config.getOptionalValue(PLATFORM_PREFIX + "process-id-generation.include-agent-name", Boolean.class)
                .ifPresent(pidGen::setIncludeAgentName);

        // Autonomy
        AgentPlatformProperties.AutonomyConfig autonomy = props.getAutonomy();
        config.getOptionalValue(AUTONOMY_PREFIX + "agent-confidence-cut-off", Double.class)
                .ifPresent(autonomy::setAgentConfidenceCutOff);
        config.getOptionalValue(AUTONOMY_PREFIX + "goal-confidence-cut-off", Double.class)
                .ifPresent(autonomy::setGoalConfidenceCutOff);

        // Models (provider retry config)
        AgentPlatformProperties.ModelsConfig models = props.getModels();
        config.getOptionalValue(PLATFORM_PREFIX + "models.anthropic.max-attempts", Integer.class)
                .ifPresent(models.getAnthropic()::setMaxAttempts);
        config.getOptionalValue(PLATFORM_PREFIX + "models.anthropic.backoff-millis", Long.class)
                .ifPresent(models.getAnthropic()::setBackoffMillis);
        config.getOptionalValue(PLATFORM_PREFIX + "models.anthropic.backoff-multiplier", Double.class)
                .ifPresent(models.getAnthropic()::setBackoffMultiplier);
        config.getOptionalValue(PLATFORM_PREFIX + "models.anthropic.backoff-max-interval", Long.class)
                .ifPresent(models.getAnthropic()::setBackoffMaxInterval);
        config.getOptionalValue(PLATFORM_PREFIX + "models.openai.max-attempts", Integer.class)
                .ifPresent(models.getOpenai()::setMaxAttempts);
        config.getOptionalValue(PLATFORM_PREFIX + "models.openai.backoff-millis", Long.class)
                .ifPresent(models.getOpenai()::setBackoffMillis);
        config.getOptionalValue(PLATFORM_PREFIX + "models.openai.backoff-multiplier", Double.class)
                .ifPresent(models.getOpenai()::setBackoffMultiplier);
        config.getOptionalValue(PLATFORM_PREFIX + "models.openai.backoff-max-interval", Long.class)
                .ifPresent(models.getOpenai()::setBackoffMaxInterval);

        // SSE
        AgentPlatformProperties.SseConfig sse = props.getSse();
        config.getOptionalValue(PLATFORM_PREFIX + "sse.max-buffer-size", Integer.class)
                .ifPresent(sse::setMaxBufferSize);
        config.getOptionalValue(PLATFORM_PREFIX + "sse.max-process-buffers", Integer.class)
                .ifPresent(sse::setMaxProcessBuffers);

        // REST
        AgentPlatformProperties.RestConfig rest = props.getRest();
        config.getOptionalValue(PLATFORM_PREFIX + "rest.process-status-enabled", Boolean.class)
                .ifPresent(rest::setProcessStatusEnabled);
        config.getOptionalValue(PLATFORM_PREFIX + "rest.process-kill-enabled", Boolean.class)
                .ifPresent(rest::setProcessKillEnabled);
        config.getOptionalValue(PLATFORM_PREFIX + "rest.process-events-enabled", Boolean.class)
                .ifPresent(rest::setProcessEventsEnabled);

        // Test
        config.getOptionalValue(PLATFORM_PREFIX + "test.mock-mode", Boolean.class)
                .ifPresent(props.getTest()::setMockMode);

        // Action QoS defaults
        AgentPlatformProperties.ActionQosProperties actionQos = props.getActionQos();
        ActionProperties aqDefault = actionQos.getDefault();
        config.getOptionalValue(PLATFORM_PREFIX + "action-qos.default.max-attempts", Integer.class)
                .ifPresent(aqDefault::setMaxAttempts);
        config.getOptionalValue(PLATFORM_PREFIX + "action-qos.default.backoff-millis", Long.class)
                .ifPresent(aqDefault::setBackoffMillis);
        config.getOptionalValue(PLATFORM_PREFIX + "action-qos.default.backoff-multiplier", Double.class)
                .ifPresent(aqDefault::setBackoffMultiplier);
        config.getOptionalValue(PLATFORM_PREFIX + "action-qos.default.backoff-max-interval", Long.class)
                .ifPresent(aqDefault::setBackoffMaxInterval);
        config.getOptionalValue(PLATFORM_PREFIX + "action-qos.default.idempotent", Boolean.class)
                .ifPresent(aqDefault::setIdempotent);

        // Threading
        AgentPlatformProperties.ThreadingProperties threading = props.getThreading();
        config.getOptionalValue(PLATFORM_PREFIX + "threading.override", Boolean.class)
                .ifPresent(threading::setOverride);
        config.getOptionalValue(PLATFORM_PREFIX + "threading.shared", Boolean.class)
                .ifPresent(threading::setShared);

        this.platformProperties = props;
    }

    /**
     * Produces {@link AgentPlatformProperties} fully populated from
     * {@code embabel.agent.platform.*} properties.
     */
    @Produces
    @ApplicationScoped
    public AgentPlatformProperties agentPlatformProperties() {
        return platformProperties;
    }

    /**
     * Produces {@link AutonomyProperties} from the autonomy section of {@link AgentPlatformProperties}.
     */
    @Produces
    @ApplicationScoped
    public AutonomyProperties autonomyProperties() {
        return new AutonomyProperties(platformProperties);
    }

    /**
     * Produces {@link Autonomy} — the service for dynamic agent and goal selection.
     */
    @Produces
    @ApplicationScoped
    public Autonomy autonomy(
            AgentPlatform agentPlatform,
            Ranker ranker,
            AutonomyProperties properties) {
        return new Autonomy(agentPlatform, ranker, properties);
    }

    /**
     * Produces {@link RankingProperties} from the ranking section of {@link AgentPlatformProperties}.
     */
    @Produces
    @ApplicationScoped
    public RankingProperties rankingProperties() {
        AgentPlatformProperties.RankingConfig ranking = platformProperties.getRanking();
        return new RankingProperties(
                ranking.getLlm(),
                ranking.getMaxAttempts(),
                ranking.getBackoffMillis(),
                ranking.getBackoffMultiplier(),
                ranking.getBackoffMaxInterval(),
                rankingPropertyPrefix);
    }

    /**
     * Produces {@link ConfigurableModelProviderProperties} for model configuration.
     * <p>
     * Manually binds properties from application.properties because
     * {@code quarkus-spring-boot-properties} doesn't bind to Kotlin data classes properly.
     * <p>
     * Supported properties:
     * <ul>
     * <li>{@code embabel.models.default-llm} - Default LLM model name</li>
     * <li>{@code embabel.models.default-embedding-model} - Default embedding model name (optional)</li>
     * <li>{@code embabel.models.llms.{role}={model-name}} - LLM role mappings</li>
     * <li>{@code embabel.models.embedding-services.{role}={service-name}} - Embedding service role mappings</li>
     * </ul>
     * <p>
     * Example configuration:
     *
     * <pre>
     * embabel.models.default-llm=gpt-4o-mini
     * embabel.models.default-embedding-model=text-embedding-ada-002
     * embabel.models.llms.best=gpt-4o
     * embabel.models.llms.fast=gpt-4o-mini
     * embabel.models.embedding-services.best=text-embedding-3-large
     * </pre>
     *
     * @return the model provider configuration properties bound from application.properties
     */
    @Produces
    @ApplicationScoped
    public ConfigurableModelProviderProperties modelProviderProperties() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        Set<String> consumedProps = new HashSet<>();

        // Read simple properties
        String defaultLlm = config
                .getOptionalValue(MODELS_PREFIX + "default-llm", String.class)
                .orElse(null);
        consumedProps.add(MODELS_PREFIX + "default-llm");

        String defaultEmbeddingModel = config
                .getOptionalValue(MODELS_PREFIX + "default-embedding-model", String.class)
                .orElse(null);
        consumedProps.add(MODELS_PREFIX + "default-embedding-model");

        // Scan for LLM role mappings (embabel.models.llms.*)
        Map<String, String> llms = scanMapProperties(config, MODELS_PREFIX + "llms.", consumedProps);

        // Scan for embedding service role mappings (embabel.models.embedding-services.*)
        Map<String, String> embeddingServices = scanMapProperties(config, MODELS_PREFIX + "embedding-services.",
                consumedProps);

        // Validate no unknown properties
        validateNoUnknownProperties(config, MODELS_PREFIX, consumedProps, "ConfigurableModelProviderProperties");

        // Construct using Kotlin data class constructor
        return new ConfigurableModelProviderProperties(
                llms,
                embeddingServices,
                defaultLlm,
                defaultEmbeddingModel);
    }

    /**
     * Produces {@link ToolLoopConfiguration} from properties with prefix
     * {@code embabel.agent.platform.toolloop}.
     * <p>
     * Example configuration:
     *
     * <pre>
     * embabel.agent.platform.toolloop.type=PARALLEL
     * embabel.agent.platform.toolloop.max-iterations=20
     * embabel.agent.platform.toolloop.parallel.per-tool-timeout=30s
     * embabel.agent.platform.toolloop.tool-not-found.max-retries=3
     * embabel.agent.platform.toolloop.empty-response.max-retries=0
     * </pre>
     *
     * @return configured tool loop configuration
     */
    @Produces
    @ApplicationScoped
    public ToolLoopConfiguration toolLoopConfiguration() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        Set<String> consumedProps = new HashSet<>();

        // Read basic properties with defaults from ToolLoopConfiguration
        ToolLoopConfiguration.ToolLoopType type = config
                .getOptionalValue(TOOLLOOP_PREFIX + "type", ToolLoopConfiguration.ToolLoopType.class)
                .orElse(ToolLoopConfiguration.ToolLoopType.DEFAULT);
        consumedProps.add(TOOLLOOP_PREFIX + "type");

        int maxIterations = config
                .getOptionalValue(TOOLLOOP_PREFIX + "max-iterations", Integer.class)
                .orElse(20);
        consumedProps.add(TOOLLOOP_PREFIX + "max-iterations");

        // Build parallel mode properties
        ToolLoopConfiguration.ParallelModeProperties parallelProps = buildParallelProperties(config, consumedProps);

        // Build tool-not-found properties
        ToolLoopConfiguration.ToolNotFoundProperties toolNotFoundProps = buildToolNotFoundProperties(config, consumedProps);

        // Build empty-response properties
        ToolLoopConfiguration.EmptyResponseProperties emptyResponseProps = buildEmptyResponseProperties(config,
                consumedProps);

        // Validate no unknown properties
        validateNoUnknownProperties(config, TOOLLOOP_PREFIX, consumedProps, "ToolLoopConfiguration");

        // Construct using Kotlin data class constructor
        return new ToolLoopConfiguration(
                type,
                maxIterations,
                parallelProps,
                toolNotFoundProps,
                emptyResponseProps);
    }

    private ToolLoopConfiguration.ParallelModeProperties buildParallelProperties(SmallRyeConfig config,
            Set<String> consumedProps) {
        Duration perToolTimeout = config
                .getOptionalValue(TOOLLOOP_PREFIX + "parallel.per-tool-timeout", Duration.class)
                .orElse(Duration.ofSeconds(30));
        consumedProps.add(TOOLLOOP_PREFIX + "parallel.per-tool-timeout");

        Duration batchTimeout = config
                .getOptionalValue(TOOLLOOP_PREFIX + "parallel.batch-timeout", Duration.class)
                .orElse(Duration.ofSeconds(60));
        consumedProps.add(TOOLLOOP_PREFIX + "parallel.batch-timeout");

        // Deprecated properties - use defaults
        return new ToolLoopConfiguration.ParallelModeProperties(
                perToolTimeout,
                batchTimeout,
                ToolLoopConfiguration.ExecutorType.VIRTUAL, // deprecated
                10, // deprecated
                Duration.ofSeconds(5) // deprecated
        );
    }

    private ToolLoopConfiguration.ToolNotFoundProperties buildToolNotFoundProperties(SmallRyeConfig config,
            Set<String> consumedProps) {
        int maxRetries = config
                .getOptionalValue(TOOLLOOP_PREFIX + "tool-not-found.max-retries", Integer.class)
                .orElse(3);
        consumedProps.add(TOOLLOOP_PREFIX + "tool-not-found.max-retries");

        Integer minFuzzyLength = config
                .getOptionalValue(TOOLLOOP_PREFIX + "tool-not-found.min-fuzzy-length", Integer.class)
                .orElse(null);
        consumedProps.add(TOOLLOOP_PREFIX + "tool-not-found.min-fuzzy-length");

        Integer minTokenLength = config
                .getOptionalValue(TOOLLOOP_PREFIX + "tool-not-found.min-token-length", Integer.class)
                .orElse(null);
        consumedProps.add(TOOLLOOP_PREFIX + "tool-not-found.min-token-length");

        double minTokenSimilarity = config
                .getOptionalValue(TOOLLOOP_PREFIX + "tool-not-found.min-token-similarity", Double.class)
                .orElse(0.25);
        consumedProps.add(TOOLLOOP_PREFIX + "tool-not-found.min-token-similarity");

        return new ToolLoopConfiguration.ToolNotFoundProperties(maxRetries, minFuzzyLength, minTokenLength, minTokenSimilarity);
    }

    private ToolLoopConfiguration.EmptyResponseProperties buildEmptyResponseProperties(SmallRyeConfig config,
            Set<String> consumedProps) {
        int maxRetries = config
                .getOptionalValue(TOOLLOOP_PREFIX + "empty-response.max-retries", Integer.class)
                .orElse(0);
        consumedProps.add(TOOLLOOP_PREFIX + "empty-response.max-retries");

        String nudgeMessage = config
                .getOptionalValue(TOOLLOOP_PREFIX + "empty-response.nudge-message", String.class)
                .orElse("Your previous turn produced no response. " +
                        "Take ONE concrete action that progresses toward answering the user's most recent question — " +
                        "either call a tool to gather what you still need, or write the answer using the information you already have. "
                        +
                        "Do not stay silent.");
        consumedProps.add(TOOLLOOP_PREFIX + "empty-response.nudge-message");

        return new ToolLoopConfiguration.EmptyResponseProperties(maxRetries, nudgeMessage);
    }

    /**
     * Scans config for all properties with the given prefix and returns them as a map.
     * <p>
     * Example: prefix {@code "embabel.models.llms."} finds:
     * <ul>
     * <li>{@code embabel.models.llms.best=gpt-4o} → {@code ("best", "gpt-4o")}</li>
     * <li>{@code embabel.models.llms.fast=gpt-4o-mini} → {@code ("fast", "gpt-4o-mini")}</li>
     * </ul>
     *
     * @param config the SmallRye config
     * @param prefix the property prefix to scan for
     * @param consumedProps set to track consumed property names
     * @return map of suffix → value
     */
    private Map<String, String> scanMapProperties(SmallRyeConfig config, String prefix, Set<String> consumedProps) {
        Map<String, String> result = new HashMap<>();
        config.getPropertyNames().forEach(propertyName -> {
            if (propertyName.startsWith(prefix)) {
                String key = propertyName.substring(prefix.length());
                config.getOptionalValue(propertyName, String.class)
                        .ifPresent(value -> {
                            result.put(key, value);
                            consumedProps.add(propertyName);
                        });
            }
        });
        return result;
    }

    /**
     * Validates that no unknown properties exist with the given prefix.
     * Logs warnings for any properties that weren't consumed during binding.
     *
     * @param config the SmallRye config
     * @param prefix the property prefix to check
     * @param consumedProps set of property names that were consumed
     * @param configClassName the config class name for logging
     */
    private void validateNoUnknownProperties(SmallRyeConfig config, String prefix, Set<String> consumedProps,
            String configClassName) {
        int unknownCount = 0;
        for (String propertyName : config.getPropertyNames()) {
            if (propertyName.startsWith(prefix) && !consumedProps.contains(propertyName)) {
                log.warnf("Unknown %s property: %s (this property will be ignored and may indicate a binding issue)",
                        configClassName, propertyName);
                unknownCount++;
            }
        }
        if (log.isDebugEnabled() && unknownCount == 0) {
            log.debugf("All %s properties with prefix '%s' were recognized (consumed: %d)", configClassName, prefix,
                    consumedProps.size());
        }
    }
}
