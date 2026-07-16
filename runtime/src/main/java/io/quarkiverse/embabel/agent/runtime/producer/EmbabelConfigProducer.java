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

    private static final String RANKING_PREFIX = "embabel.agent.platform.ranking.";
    private static final String TOOLLOOP_PREFIX = "embabel.agent.platform.toolloop.";
    private static final String MODELS_PREFIX = "embabel.models.";
    private static final String AUTONOMY_PREFIX = "embabel.agent.platform.autonomy.";

    /**
     * Produces {@link AgentPlatformProperties} for autonomy configuration.
     * Reads configuration from embabel.agent.platform.autonomy.* properties.
     * Only the autonomy section is populated; other sections use defaults.
     */
    @Produces
    @ApplicationScoped
    public AgentPlatformProperties agentPlatformProperties() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        AgentPlatformProperties props = new AgentPlatformProperties();

        AgentPlatformProperties.AutonomyConfig autonomy = props.getAutonomy();
        config.getOptionalValue(AUTONOMY_PREFIX + "agent-confidence-cut-off", Double.class)
                .ifPresent(autonomy::setAgentConfidenceCutOff);
        config.getOptionalValue(AUTONOMY_PREFIX + "goal-confidence-cut-off", Double.class)
                .ifPresent(autonomy::setGoalConfidenceCutOff);

        return props;
    }

    /**
     * Produces {@link AutonomyProperties} wrapping the two confidence thresholds.
     * Defaults to 0.6 for both agent and goal confidence cut-offs.
     */
    @Produces
    @ApplicationScoped
    public AutonomyProperties autonomyProperties(AgentPlatformProperties platformProperties) {
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
     * Produces {@link RankingProperties} for configuring the ranker.
     * Reads configuration from embabel.agent.platform.ranking.* properties.
     * <p>
     * Note: This producer is needed because RankingProperties is a Kotlin data class
     * with immutable fields, which Spring Boot @ConfigurationProperties cannot bind to.
     * This producer overrides the Spring Boot properties bean with @Alternative/@Priority.
     *
     * @param llm LLM name for ranking, or empty to use auto-selection
     * @param maxAttempts maximum retry attempts
     * @param backoffMillis initial backoff time in milliseconds
     * @param backoffMultiplier backoff multiplier for exponential backoff
     * @param backoffMaxInterval maximum backoff interval in milliseconds
     * @return the ranking properties
     */
    @Produces
    @ApplicationScoped
    public RankingProperties rankingProperties() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        String llm = config
                .getOptionalValue(RANKING_PREFIX + "llm", String.class)
                .orElse(null);

        int maxAttempts = config
                .getOptionalValue(RANKING_PREFIX + "max-attempts", Integer.class)
                .orElse(5);

        long backoffMillis = config
                .getOptionalValue(RANKING_PREFIX + "backoff-millis", Long.class)
                .orElse(100L);

        double backoffMultiplier = config
                .getOptionalValue(RANKING_PREFIX + "backoff-multiplier", Double.class)
                .orElse(5.0);

        long backoffMaxInterval = config
                .getOptionalValue(RANKING_PREFIX + "backoff-max-interval", Long.class)
                .orElse(180000L);

        String propertyPrefix = config
                .getOptionalValue(RANKING_PREFIX + "property-prefix", String.class)
                .orElse(null);

        return new RankingProperties(
                llm,
                maxAttempts,
                backoffMillis,
                backoffMultiplier,
                backoffMaxInterval,
                propertyPrefix);
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
