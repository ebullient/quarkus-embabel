package io.quarkiverse.embabel.agent.runtime.producer;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.eclipse.microprofile.config.ConfigProvider;

import com.embabel.agent.spi.config.spring.AgentPlatformProperties.ActionQosProperties.ActionProperties;

import io.quarkiverse.embabel.agent.runtime.config.NamedActionQosConfig;
import io.quarkiverse.embabel.agent.runtime.qos.QuarkusActionQosPropertyProvider;
import io.smallrye.config.SmallRyeConfig;

/**
 * CDI producer for Action QoS configuration beans.
 * <p>
 * Produces named Action QoS configurations from
 * {@code embabel.agent.platform.action-qos.{name}.*} properties, and a
 * {@link QuarkusActionQosPropertyProvider} for resolving them.
 * <p>
 * Default Action QoS configuration is read from {@link AgentPlatformProperties}
 * (populated by {@link EmbabelConfigProducer}).
 *
 * @see NamedActionQosConfig
 * @see QuarkusActionQosPropertyProvider
 */
@ApplicationScoped
public class ActionQosProducer {

    private static final String ACTION_QOS_PREFIX = "embabel.agent.platform.action-qos.";
    private static final String DEFAULT_PREFIX = ACTION_QOS_PREFIX + "default.";

    /**
     * Produces the named Action QoS configurations.
     * <p>
     * Scans all properties matching {@code embabel.agent.platform.action-qos.{name}.*}
     * (excluding {@code default.*}) and groups them by name.
     */
    @Produces
    @ApplicationScoped
    public NamedActionQosConfig namedActionQosConfig() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        Map<String, ActionProperties> namedConfigs = new HashMap<>();

        config.getPropertyNames().forEach(propertyName -> {
            if (propertyName.startsWith(ACTION_QOS_PREFIX) && !propertyName.startsWith(DEFAULT_PREFIX)) {
                String remainder = propertyName.substring(ACTION_QOS_PREFIX.length());

                int lastDotIndex = remainder.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    String name = remainder.substring(0, lastDotIndex);
                    String field = remainder.substring(lastDotIndex + 1);

                    ActionProperties props = namedConfigs
                            .computeIfAbsent(name,
                                    k -> new ActionProperties());

                    bindField(config, propertyName, field, props);
                }
            }
        });

        return new NamedActionQosConfig(namedConfigs);
    }

    /**
     * Produces the Quarkus Action QoS property provider.
     */
    @Produces
    @ApplicationScoped
    public QuarkusActionQosPropertyProvider actionQosPropertyProvider(NamedActionQosConfig namedConfig) {
        return new QuarkusActionQosPropertyProvider(namedConfig);
    }

    private void bindField(SmallRyeConfig config, String propertyName, String field,
            ActionProperties props) {
        switch (field) {
            case "max-attempts":
                config.getOptionalValue(propertyName, Integer.class)
                        .ifPresent(props::setMaxAttempts);
                break;
            case "backoff-millis":
                config.getOptionalValue(propertyName, Long.class)
                        .ifPresent(props::setBackoffMillis);
                break;
            case "backoff-multiplier":
                config.getOptionalValue(propertyName, Double.class)
                        .ifPresent(props::setBackoffMultiplier);
                break;
            case "backoff-max-interval":
                config.getOptionalValue(propertyName, Long.class)
                        .ifPresent(props::setBackoffMaxInterval);
                break;
            case "idempotent":
                config.getOptionalValue(propertyName, Boolean.class)
                        .ifPresent(props::setIdempotent);
                break;
        }
    }
}
