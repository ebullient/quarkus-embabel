package io.quarkiverse.embabel.agent.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import com.embabel.agent.spi.config.spring.AgentPlatformProperties;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.config.SmallRyeConfig;

/**
 * Tests for Action QoS config binding with SmallRye Config.
 * <p>
 * Verifies that properties with pattern {@code embabel.agent.platform.action-qos.{name}.{field}}
 * are correctly parsed into {@link AgentPlatformProperties.ActionQosProperties.ActionProperties}.
 */
@QuarkusTest
@TestProfile(ActionQosConfigBindingTest.ConfigProfile.class)
class ActionQosConfigBindingTest {

    public static class ConfigProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = new HashMap<>();

            config.put("embabel.agent.platform.action-qos.default.max-attempts", "3");
            config.put("embabel.agent.platform.action-qos.default.backoff-millis", "1000");
            config.put("embabel.agent.platform.action-qos.default.backoff-multiplier", "2.0");
            config.put("embabel.agent.platform.action-qos.default.backoff-max-interval", "30000");
            config.put("embabel.agent.platform.action-qos.default.idempotent", "false");

            config.put("embabel.agent.platform.action-qos.fast-retry.max-attempts", "5");
            config.put("embabel.agent.platform.action-qos.fast-retry.backoff-millis", "100");
            config.put("embabel.agent.platform.action-qos.fast-retry.backoff-multiplier", "1.5");
            config.put("embabel.agent.platform.action-qos.fast-retry.backoff-max-interval", "5000");
            config.put("embabel.agent.platform.action-qos.fast-retry.idempotent", "true");

            config.put("embabel.agent.platform.action-qos.agents.agent.method.max-attempts", "2");
            config.put("embabel.agent.platform.action-qos.agents.agent.method.backoff-millis", "500");
            config.put("embabel.agent.platform.action-qos.agents.agent.method.backoff-multiplier", "3.0");
            config.put("embabel.agent.platform.action-qos.agents.agent.method.backoff-max-interval", "10000");
            config.put("embabel.agent.platform.action-qos.agents.agent.method.idempotent", "false");

            config.put("embabel.agent.platform.action-qos.partial.max-attempts", "7");
            config.put("embabel.agent.platform.action-qos.partial.idempotent", "true");

            return config;
        }
    }

    @Test
    void bindDefaultConfig_allFieldsPresent_parsesCorrectly() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        var props = bindNamedConfig(config, "default");

        assertThat(props.getMaxAttempts()).isEqualTo(3);
        assertThat(props.getBackoffMillis()).isEqualTo(1000L);
        assertThat(props.getBackoffMultiplier()).isEqualTo(2.0);
        assertThat(props.getBackoffMaxInterval()).isEqualTo(30000L);
        assertThat(props.getIdempotent()).isFalse();
    }

    @Test
    void bindSimpleNamedConfig_allFieldsPresent_parsesCorrectly() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        var props = bindNamedConfig(config, "fast-retry");

        assertThat(props.getMaxAttempts()).isEqualTo(5);
        assertThat(props.getBackoffMillis()).isEqualTo(100L);
        assertThat(props.getBackoffMultiplier()).isEqualTo(1.5);
        assertThat(props.getBackoffMaxInterval()).isEqualTo(5000L);
        assertThat(props.getIdempotent()).isTrue();
    }

    @Test
    void bindHierarchicalNamedConfig_allFieldsPresent_parsesCorrectly() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        var props = bindNamedConfig(config, "agents.agent.method");

        assertThat(props.getMaxAttempts()).isEqualTo(2);
        assertThat(props.getBackoffMillis()).isEqualTo(500L);
        assertThat(props.getBackoffMultiplier()).isEqualTo(3.0);
        assertThat(props.getBackoffMaxInterval()).isEqualTo(10000L);
        assertThat(props.getIdempotent()).isFalse();
    }

    @Test
    void bindPartialConfig_onlySomeFieldsPresent_parsesConfiguredFieldsOnly() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        var props = bindNamedConfig(config, "partial");

        assertThat(props.getMaxAttempts()).isEqualTo(7);
        assertThat(props.getIdempotent()).isTrue();
        assertThat(props.getBackoffMillis()).isNull();
        assertThat(props.getBackoffMultiplier()).isNull();
        assertThat(props.getBackoffMaxInterval()).isNull();
    }

    @Test
    void bindMissingConfig_noPropertiesPresent_returnsEmptyConfig() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        var props = bindNamedConfig(config, "nonexistent");

        assertThat(props.getMaxAttempts()).isNull();
        assertThat(props.getBackoffMillis()).isNull();
        assertThat(props.getBackoffMultiplier()).isNull();
        assertThat(props.getBackoffMaxInterval()).isNull();
        assertThat(props.getIdempotent()).isNull();
    }

    @Test
    void namedActionQosConfig_withMultipleEntries_storesAllCorrectly() {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);

        NamedActionQosConfig namedConfigs = new NamedActionQosConfig();
        namedConfigs.put("fast-retry", bindNamedConfig(config, "fast-retry"));
        namedConfigs.put("agents.agent.method", bindNamedConfig(config, "agents.agent.method"));
        namedConfigs.put("partial", bindNamedConfig(config, "partial"));

        assertThat(namedConfigs.size()).isEqualTo(3);
        assertThat(namedConfigs.contains("fast-retry")).isTrue();
        assertThat(namedConfigs.contains("agents.agent.method")).isTrue();
        assertThat(namedConfigs.contains("partial")).isTrue();

        assertThat(namedConfigs.get("fast-retry").getMaxAttempts()).isEqualTo(5);
        assertThat(namedConfigs.get("agents.agent.method").getMaxAttempts()).isEqualTo(2);
        assertThat(namedConfigs.get("partial").getMaxAttempts()).isEqualTo(7);
        assertThat(namedConfigs.get("partial").getBackoffMillis()).isNull();
    }

    private AgentPlatformProperties.ActionQosProperties.ActionProperties bindNamedConfig(
            SmallRyeConfig config, String name) {
        String prefix = "embabel.agent.platform.action-qos." + name;
        var props = new AgentPlatformProperties.ActionQosProperties.ActionProperties();

        config.getOptionalValue(prefix + ".max-attempts", Integer.class)
                .ifPresent(props::setMaxAttempts);
        config.getOptionalValue(prefix + ".backoff-millis", Long.class)
                .ifPresent(props::setBackoffMillis);
        config.getOptionalValue(prefix + ".backoff-multiplier", Double.class)
                .ifPresent(props::setBackoffMultiplier);
        config.getOptionalValue(prefix + ".backoff-max-interval", Long.class)
                .ifPresent(props::setBackoffMaxInterval);
        config.getOptionalValue(prefix + ".idempotent", Boolean.class)
                .ifPresent(props::setIdempotent);

        return props;
    }
}
