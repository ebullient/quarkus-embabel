package io.quarkiverse.embabel.agent.runtime.qos;

import com.embabel.agent.spi.config.spring.AgentPlatformProperties;

import io.quarkiverse.embabel.agent.runtime.config.ActionQosConfig;
import io.quarkiverse.embabel.agent.runtime.config.NamedActionQosConfig;

/**
 * Quarkus-specific implementation of Action QoS property lookup.
 * <p>
 * This replaces the Spring Binder-based {@code ActionQosPropertyProvider} with a SmallRye Config-backed
 * implementation that resolves named Action QoS configurations.
 * <p>
 * <b>Normalization:</b> Retry expressions are normalized to lookup keys:
 * <ul>
 * <li>{@code "${fast-retry}"} → {@code "fast-retry"}</li>
 * <li>{@code "fast-retry"} → {@code "fast-retry"}</li>
 * <li>{@code "agents.agent.method"} → {@code "agents.agent.method"}</li>
 * </ul>
 * <p>
 * <b>Lookup:</b> The normalized key is used to look up a named configuration from
 * {@link NamedActionQosConfig}, which is populated from properties matching:
 * {@code embabel.agent.platform.action-qos.{name}.{field}}
 * <p>
 * <b>Conversion:</b> The extension-owned {@link ActionQosConfig} DTO is converted to the upstream
 * {@link com.embabel.agent.spi.config.spring.AgentPlatformProperties.ActionQosProperties.ActionProperties}
 * type for compatibility with {@code DefaultActionQosProvider}.
 * <p>
 * <b>Note:</b> This class is not annotated with {@code @ApplicationScoped} because it is produced
 * by {@link io.quarkiverse.embabel.agent.runtime.producer.ActionQosProducer}.
 *
 * @see com.embabel.agent.api.annotation.support.ActionQosPropertyProvider
 * @see com.embabel.agent.api.annotation.support.DefaultActionQosProvider
 * @see io.quarkiverse.embabel.agent.runtime.producer.ActionQosProducer
 */
public class QuarkusActionQosPropertyProvider {

    private final NamedActionQosConfig namedConfig;

    /**
     * Constructor for CDI producer.
     *
     * @param namedConfig the named Action QoS configuration map
     */
    public QuarkusActionQosPropertyProvider(NamedActionQosConfig namedConfig) {
        this.namedConfig = namedConfig;
    }

    /**
     * Get bound Action QoS properties for the given retry expression.
     * <p>
     * This method mirrors the behavior of
     * {@code ActionQosPropertyProvider.getBound(String)} but uses SmallRye Config
     * instead of Spring Binder.
     *
     * @param expr the retry expression from {@code @Agent} or {@code @Action} annotation
     * @return the bound ActionProperties, or null if expression is blank or name not found
     */
    public AgentPlatformProperties.ActionQosProperties.ActionProperties getBound(String expr) {
        // Blank expressions return null (no override)
        if (expr == null || expr.isBlank()) {
            return null;
        }

        // Normalize expression to lookup key
        String normalizedName = normalizeExpression(expr);

        // Look up named configuration
        ActionQosConfig qosConfig = namedConfig.get(normalizedName);
        if (qosConfig == null) {
            // Missing configuration returns null (fallback to defaults)
            return null;
        }

        // Convert to upstream ActionProperties type
        return toActionProperties(qosConfig);
    }

    /**
     * Normalize a retry expression to a lookup key.
     * <p>
     * Removes {@code ${...}} wrapper if present and trims whitespace.
     * <p>
     * Examples:
     * <ul>
     * <li>{@code "${fast-retry}"} → {@code "fast-retry"}</li>
     * <li>{@code "fast-retry"} → {@code "fast-retry"}</li>
     * <li>{@code "${agents.agent.method}"} → {@code "agents.agent.method"}</li>
     * <li>{@code "  agents.agent.method  "} → {@code "agents.agent.method"}</li>
     * </ul>
     *
     * @param expr the retry expression
     * @return the normalized lookup key
     */
    String normalizeExpression(String expr) {
        String trimmed = expr.trim();

        // Remove ${ } wrapper if present
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return trimmed.substring(2, trimmed.length() - 1).trim();
        }

        return trimmed;
    }

    /**
     * Convert extension-owned ActionQosConfig to upstream ActionProperties.
     * <p>
     * This conversion preserves partial override semantics: only non-null fields
     * from the config DTO are set in the resulting ActionProperties.
     * <p>
     * Note: This is a temporary inline conversion. Step 4 will extract this to
     * dedicated mapper methods.
     *
     * @param config the extension-owned config DTO
     * @return the upstream ActionProperties instance
     */
    private AgentPlatformProperties.ActionQosProperties.ActionProperties toActionProperties(ActionQosConfig config) {
        AgentPlatformProperties.ActionQosProperties.ActionProperties props = new AgentPlatformProperties.ActionQosProperties.ActionProperties();

        // Only set non-null fields to preserve partial override semantics
        if (config.getMaxAttempts() != null) {
            props.setMaxAttempts(config.getMaxAttempts());
        }
        if (config.getBackoffMillis() != null) {
            props.setBackoffMillis(config.getBackoffMillis());
        }
        if (config.getBackoffMultiplier() != null) {
            props.setBackoffMultiplier(config.getBackoffMultiplier());
        }
        if (config.getBackoffMaxInterval() != null) {
            props.setBackoffMaxInterval(config.getBackoffMaxInterval());
        }
        if (config.getIdempotent() != null) {
            props.setIdempotent(config.getIdempotent());
        }

        return props;
    }
}
