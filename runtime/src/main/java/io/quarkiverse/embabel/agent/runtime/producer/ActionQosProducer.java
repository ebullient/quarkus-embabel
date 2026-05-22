package io.quarkiverse.embabel.agent.runtime.producer;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.embabel.agent.runtime.config.ActionQosConfig;
import io.quarkiverse.embabel.agent.runtime.config.NamedActionQosConfig;
import io.quarkiverse.embabel.agent.runtime.qos.QuarkusActionQosPropertyProvider;
import io.smallrye.config.SmallRyeConfig;

/**
 * CDI producer for Action QoS configuration beans.
 * <p>
 * This producer creates the Action QoS configuration beans required by
 * {@link io.quarkiverse.embabel.agent.runtime.QuarkusAgentDeployer} for
 * configuring retry behavior and quality of service settings for agent actions.
 * <p>
 * The beans produced here include:
 * <ul>
 * <li>{@link ActionQosConfig} - Default Action QoS configuration from
 * {@code embabel.agent.platform.action-qos.default.*}</li>
 * <li>{@link NamedActionQosConfig} - Named Action QoS configurations from
 * {@code embabel.agent.platform.action-qos.{name}.*}</li>
 * <li>{@link QuarkusActionQosPropertyProvider} - Property provider for resolving
 * named configurations</li>
 * </ul>
 * <p>
 * <strong>Manual Configuration Binding</strong>
 * <p>
 * Uses manual SmallRye Config binding (following the pattern in {@link CoreBeansProducer#embabelProperties()})
 * because {@code quarkus-spring-boot-properties} does not reliably bind to Kotlin classes.
 * <p>
 * <strong>Property Structure</strong>
 * <p>
 * Default configuration:
 *
 * <pre>
 * embabel.agent.platform.action-qos.default.max-attempts=3
 * embabel.agent.platform.action-qos.default.backoff-millis=1000
 * embabel.agent.platform.action-qos.default.backoff-multiplier=2.0
 * embabel.agent.platform.action-qos.default.backoff-max-interval=30000
 * embabel.agent.platform.action-qos.default.idempotent=false
 * </pre>
 * <p>
 * Named configurations (simple names):
 *
 * <pre>
 * embabel.agent.platform.action-qos.fast-retry.max-attempts=5
 * embabel.agent.platform.action-qos.fast-retry.backoff-millis=100
 * </pre>
 * <p>
 * Named configurations (hierarchical names):
 *
 * <pre>
 * embabel.agent.platform.action-qos.agents.agent.method.max-attempts=10
 * embabel.agent.platform.action-qos.agents.agent.method.idempotent=true
 * </pre>
 *
 * @see ActionQosConfig
 * @see NamedActionQosConfig
 * @see QuarkusActionQosPropertyProvider
 * @see CoreBeansProducer
 */
@ApplicationScoped
public class ActionQosProducer {

    private static final String ACTION_QOS_PREFIX = "embabel.agent.platform.action-qos.";
    private static final String DEFAULT_PREFIX = ACTION_QOS_PREFIX + "default.";

    /**
     * Produces the default Action QoS configuration.
     * <p>
     * Binds properties from {@code embabel.agent.platform.action-qos.default.*}
     * using manual SmallRye Config binding.
     * <p>
     * Supported properties:
     * <ul>
     * <li>{@code max-attempts} - Maximum retry attempts</li>
     * <li>{@code backoff-millis} - Initial backoff delay in milliseconds</li>
     * <li>{@code backoff-multiplier} - Backoff multiplier for exponential backoff</li>
     * <li>{@code backoff-max-interval} - Maximum backoff interval in milliseconds</li>
     * <li>{@code idempotent} - Whether action is idempotent</li>
     * </ul>
     *
     * @return the default Action QoS configuration
     */
    @Produces
    @ApplicationScoped
    public ActionQosConfig defaultActionQosConfig() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        ActionQosConfig defaultConfig = new ActionQosConfig();

        // Bind default configuration properties
        config.getOptionalValue(DEFAULT_PREFIX + "max-attempts", Integer.class)
                .ifPresent(defaultConfig::setMaxAttempts);
        config.getOptionalValue(DEFAULT_PREFIX + "backoff-millis", Long.class)
                .ifPresent(defaultConfig::setBackoffMillis);
        config.getOptionalValue(DEFAULT_PREFIX + "backoff-multiplier", Double.class)
                .ifPresent(defaultConfig::setBackoffMultiplier);
        config.getOptionalValue(DEFAULT_PREFIX + "backoff-max-interval", Long.class)
                .ifPresent(defaultConfig::setBackoffMaxInterval);
        config.getOptionalValue(DEFAULT_PREFIX + "idempotent", Boolean.class)
                .ifPresent(defaultConfig::setIdempotent);

        return defaultConfig;
    }

    /**
     * Produces the named Action QoS configurations.
     * <p>
     * Scans all properties matching {@code embabel.agent.platform.action-qos.{name}.*}
     * and groups them by name to create a map of named configurations.
     * <p>
     * Supports both simple names (e.g., "fast-retry") and hierarchical names
     * (e.g., "agents.agent.method").
     * <p>
     * Example properties:
     *
     * <pre>
     * embabel.agent.platform.action-qos.fast-retry.max-attempts=5
     * embabel.agent.platform.action-qos.fast-retry.backoff-millis=100
     * embabel.agent.platform.action-qos.agents.agent.method.max-attempts=10
     * </pre>
     *
     * @return the named Action QoS configurations
     */
    @Produces
    @ApplicationScoped
    public NamedActionQosConfig namedActionQosConfig() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        Map<String, ActionQosConfig> namedConfigs = new HashMap<>();

        // Scan all properties starting with action-qos prefix
        config.getPropertyNames().forEach(propertyName -> {
            if (propertyName.startsWith(ACTION_QOS_PREFIX) && !propertyName.startsWith(DEFAULT_PREFIX)) {
                // Extract name and field from property
                // Format: embabel.agent.platform.action-qos.{name}.{field}
                String remainder = propertyName.substring(ACTION_QOS_PREFIX.length());

                // Find the last dot to separate name from field
                int lastDotIndex = remainder.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    String name = remainder.substring(0, lastDotIndex);
                    String field = remainder.substring(lastDotIndex + 1);

                    // Get or create config for this name
                    ActionQosConfig qosConfig = namedConfigs.computeIfAbsent(name, k -> new ActionQosConfig());

                    // Bind the field value
                    bindField(config, propertyName, field, qosConfig);
                }
            }
        });

        return new NamedActionQosConfig(namedConfigs);
    }

    /**
     * Produces the Quarkus Action QoS property provider.
     * <p>
     * This provider is injected with the named configurations and used by
     * {@link com.embabel.agent.api.annotation.support.DefaultActionQosProvider}
     * to resolve retry expressions like {@code ${fast-retry}} or {@code fast-retry}.
     *
     * @param namedConfig the named Action QoS configurations
     * @return the property provider
     */
    @Produces
    @ApplicationScoped
    public QuarkusActionQosPropertyProvider actionQosPropertyProvider(NamedActionQosConfig namedConfig) {
        return new QuarkusActionQosPropertyProvider(namedConfig);
    }

    /**
     * Bind a single field value from config to the ActionQosConfig.
     *
     * @param config the SmallRye config
     * @param propertyName the full property name
     * @param field the field name (e.g., "max-attempts")
     * @param qosConfig the config to bind to
     */
    private void bindField(SmallRyeConfig config, String propertyName, String field, ActionQosConfig qosConfig) {
        switch (field) {
            case "max-attempts":
                config.getOptionalValue(propertyName, Integer.class)
                        .ifPresent(qosConfig::setMaxAttempts);
                break;
            case "backoff-millis":
                config.getOptionalValue(propertyName, Long.class)
                        .ifPresent(qosConfig::setBackoffMillis);
                break;
            case "backoff-multiplier":
                config.getOptionalValue(propertyName, Double.class)
                        .ifPresent(qosConfig::setBackoffMultiplier);
                break;
            case "backoff-max-interval":
                config.getOptionalValue(propertyName, Long.class)
                        .ifPresent(qosConfig::setBackoffMaxInterval);
                break;
            case "idempotent":
                config.getOptionalValue(propertyName, Boolean.class)
                        .ifPresent(qosConfig::setIdempotent);
                break;
            // Ignore unknown fields
        }
    }
}