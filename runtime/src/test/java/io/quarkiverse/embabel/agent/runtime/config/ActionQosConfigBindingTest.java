package io.quarkiverse.embabel.agent.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.config.SmallRyeConfig;

/**
 * Unit tests for Action QoS config DTOs with SmallRye Config binding.
 * <p>
 * Tests verify that properties with pattern {@code embabel.agent.platform.action-qos.{name}.{field}}
 * are correctly parsed into {@link ActionQosConfig} instances, including:
 * <ul>
 * <li>Simple names (e.g., "fast-retry")</li>
 * <li>Hierarchical names (e.g., "agents.agent.method")</li>
 * <li>Partial field population (only configured fields are set)</li>
 * <li>Missing names (no configuration)</li>
 * </ul>
 * <p>
 * These tests focus on property-to-config binding behavior, not on plain object construction.
 */
@QuarkusTest
@TestProfile(ActionQosConfigBindingTest.ConfigProfile.class)
class ActionQosConfigBindingTest {

    /**
     * Test profile that provides Action QoS configuration properties.
     */
    public static class ConfigProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = new HashMap<>();

            // Default configuration
            config.put("embabel.agent.platform.action-qos.default.max-attempts", "3");
            config.put("embabel.agent.platform.action-qos.default.backoff-millis", "1000");
            config.put("embabel.agent.platform.action-qos.default.backoff-multiplier", "2.0");
            config.put("embabel.agent.platform.action-qos.default.backoff-max-interval", "30000");
            config.put("embabel.agent.platform.action-qos.default.idempotent", "false");

            // Simple named configuration - complete
            config.put("embabel.agent.platform.action-qos.fast-retry.max-attempts", "5");
            config.put("embabel.agent.platform.action-qos.fast-retry.backoff-millis", "100");
            config.put("embabel.agent.platform.action-qos.fast-retry.backoff-multiplier", "1.5");
            config.put("embabel.agent.platform.action-qos.fast-retry.backoff-max-interval", "5000");
            config.put("embabel.agent.platform.action-qos.fast-retry.idempotent", "true");

            // Hierarchical named configuration - complete
            config.put("embabel.agent.platform.action-qos.agents.agent.method.max-attempts", "2");
            config.put("embabel.agent.platform.action-qos.agents.agent.method.backoff-millis", "500");
            config.put("embabel.agent.platform.action-qos.agents.agent.method.backoff-multiplier", "3.0");
            config.put("embabel.agent.platform.action-qos.agents.agent.method.backoff-max-interval", "10000");
            config.put("embabel.agent.platform.action-qos.agents.agent.method.idempotent", "false");

            // Partial configuration - only some fields
            config.put("embabel.agent.platform.action-qos.partial.max-attempts", "7");
            config.put("embabel.agent.platform.action-qos.partial.idempotent", "true");

            return config;
        }
    }

    @Test
    void bindDefaultConfig_allFieldsPresent_parsesCorrectly() {
        // Given
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        // When - manually bind default config
        ActionQosConfig defaultConfig = new ActionQosConfig();
        config.getOptionalValue("embabel.agent.platform.action-qos.default.max-attempts", Integer.class)
                .ifPresent(defaultConfig::setMaxAttempts);
        config.getOptionalValue("embabel.agent.platform.action-qos.default.backoff-millis", Long.class)
                .ifPresent(defaultConfig::setBackoffMillis);
        config.getOptionalValue("embabel.agent.platform.action-qos.default.backoff-multiplier", Double.class)
                .ifPresent(defaultConfig::setBackoffMultiplier);
        config.getOptionalValue("embabel.agent.platform.action-qos.default.backoff-max-interval", Long.class)
                .ifPresent(defaultConfig::setBackoffMaxInterval);
        config.getOptionalValue("embabel.agent.platform.action-qos.default.idempotent", Boolean.class)
                .ifPresent(defaultConfig::setIdempotent);

        // Then
        assertThat(defaultConfig.getMaxAttempts()).isEqualTo(3);
        assertThat(defaultConfig.getBackoffMillis()).isEqualTo(1000L);
        assertThat(defaultConfig.getBackoffMultiplier()).isEqualTo(2.0);
        assertThat(defaultConfig.getBackoffMaxInterval()).isEqualTo(30000L);
        assertThat(defaultConfig.getIdempotent()).isFalse();
    }

    @Test
    void bindSimpleNamedConfig_allFieldsPresent_parsesCorrectly() {
        // Given
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        String name = "fast-retry";

        // When - manually bind named config
        ActionQosConfig namedConfig = bindNamedConfig(config, name);

        // Then
        assertThat(namedConfig.getMaxAttempts()).isEqualTo(5);
        assertThat(namedConfig.getBackoffMillis()).isEqualTo(100L);
        assertThat(namedConfig.getBackoffMultiplier()).isEqualTo(1.5);
        assertThat(namedConfig.getBackoffMaxInterval()).isEqualTo(5000L);
        assertThat(namedConfig.getIdempotent()).isTrue();
    }

    @Test
    void bindHierarchicalNamedConfig_allFieldsPresent_parsesCorrectly() {
        // Given
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        String name = "agents.agent.method";

        // When - manually bind hierarchical named config
        ActionQosConfig namedConfig = bindNamedConfig(config, name);

        // Then
        assertThat(namedConfig.getMaxAttempts()).isEqualTo(2);
        assertThat(namedConfig.getBackoffMillis()).isEqualTo(500L);
        assertThat(namedConfig.getBackoffMultiplier()).isEqualTo(3.0);
        assertThat(namedConfig.getBackoffMaxInterval()).isEqualTo(10000L);
        assertThat(namedConfig.getIdempotent()).isFalse();
    }

    @Test
    void bindPartialConfig_onlySomeFieldsPresent_parsesConfiguredFieldsOnly() {
        // Given
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        String name = "partial";

        // When - manually bind partial config
        ActionQosConfig partialConfig = bindNamedConfig(config, name);

        // Then - only configured fields are set
        assertThat(partialConfig.getMaxAttempts()).isEqualTo(7);
        assertThat(partialConfig.getIdempotent()).isTrue();

        // Unconfigured fields remain null
        assertThat(partialConfig.getBackoffMillis()).isNull();
        assertThat(partialConfig.getBackoffMultiplier()).isNull();
        assertThat(partialConfig.getBackoffMaxInterval()).isNull();
    }

    @Test
    void bindMissingConfig_noPropertiesPresent_returnsEmptyConfig() {
        // Given
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        String name = "nonexistent";

        // When - manually bind missing config
        ActionQosConfig missingConfig = bindNamedConfig(config, name);

        // Then - all fields are null
        assertThat(missingConfig.getMaxAttempts()).isNull();
        assertThat(missingConfig.getBackoffMillis()).isNull();
        assertThat(missingConfig.getBackoffMultiplier()).isNull();
        assertThat(missingConfig.getBackoffMaxInterval()).isNull();
        assertThat(missingConfig.getIdempotent()).isNull();
    }

    @Test
    void namedActionQosConfig_withMultipleEntries_storesAllCorrectly() {
        // Given
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        // When - build NamedActionQosConfig with multiple entries
        NamedActionQosConfig namedConfigs = new NamedActionQosConfig();
        namedConfigs.put("fast-retry", bindNamedConfig(config, "fast-retry"));
        namedConfigs.put("agents.agent.method", bindNamedConfig(config, "agents.agent.method"));
        namedConfigs.put("partial", bindNamedConfig(config, "partial"));

        // Then
        assertThat(namedConfigs.size()).isEqualTo(3);
        assertThat(namedConfigs.contains("fast-retry")).isTrue();
        assertThat(namedConfigs.contains("agents.agent.method")).isTrue();
        assertThat(namedConfigs.contains("partial")).isTrue();

        // Verify fast-retry config
        ActionQosConfig fastRetry = namedConfigs.get("fast-retry");
        assertThat(fastRetry).isNotNull();
        assertThat(fastRetry.getMaxAttempts()).isEqualTo(5);

        // Verify hierarchical config
        ActionQosConfig hierarchical = namedConfigs.get("agents.agent.method");
        assertThat(hierarchical).isNotNull();
        assertThat(hierarchical.getMaxAttempts()).isEqualTo(2);

        // Verify partial config
        ActionQosConfig partial = namedConfigs.get("partial");
        assertThat(partial).isNotNull();
        assertThat(partial.getMaxAttempts()).isEqualTo(7);
        assertThat(partial.getBackoffMillis()).isNull();
    }

    /**
     * Helper method to bind a named Action QoS config from SmallRye Config.
     * This simulates the manual binding pattern used in CoreBeansProducer.
     *
     * @param config the SmallRye config
     * @param name the configuration name
     * @return the bound ActionQosConfig
     */
    private ActionQosConfig bindNamedConfig(SmallRyeConfig config, String name) {
        String prefix = "embabel.agent.platform.action-qos." + name;
        ActionQosConfig namedConfig = new ActionQosConfig();

        config.getOptionalValue(prefix + ".max-attempts", Integer.class)
                .ifPresent(namedConfig::setMaxAttempts);
        config.getOptionalValue(prefix + ".backoff-millis", Long.class)
                .ifPresent(namedConfig::setBackoffMillis);
        config.getOptionalValue(prefix + ".backoff-multiplier", Double.class)
                .ifPresent(namedConfig::setBackoffMultiplier);
        config.getOptionalValue(prefix + ".backoff-max-interval", Long.class)
                .ifPresent(namedConfig::setBackoffMaxInterval);
        config.getOptionalValue(prefix + ".idempotent", Boolean.class)
                .ifPresent(namedConfig::setIdempotent);

        return namedConfig;
    }
}