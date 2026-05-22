package io.quarkiverse.embabel.agent.runtime.producer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.embabel.agent.runtime.config.ActionQosConfig;
import io.quarkiverse.embabel.agent.runtime.config.NamedActionQosConfig;
import io.quarkiverse.embabel.agent.runtime.qos.QuarkusActionQosPropertyProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration tests for {@link ActionQosProducer}.
 * <p>
 * Tests verify that CDI beans are created and properly wired with sample
 * application.properties containing default and named QoS entries.
 * <p>
 * These tests focus on CDI bean production and wiring, not on re-testing
 * the property parsing logic already covered in {@link io.quarkiverse.embabel.agent.runtime.config.ActionQosConfigBindingTest}.
 */
@QuarkusTest
@TestProfile(ActionQosProducerTest.ConfigProfile.class)
class ActionQosProducerTest {

    @Inject
    ActionQosConfig defaultActionQosConfig;

    @Inject
    NamedActionQosConfig namedActionQosConfig;

    @Inject
    QuarkusActionQosPropertyProvider actionQosPropertyProvider;

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

            // Simple named configuration
            config.put("embabel.agent.platform.action-qos.fast-retry.max-attempts", "5");
            config.put("embabel.agent.platform.action-qos.fast-retry.backoff-millis", "100");

            // Hierarchical named configuration
            config.put("embabel.agent.platform.action-qos.agents.agent.method.max-attempts", "10");
            config.put("embabel.agent.platform.action-qos.agents.agent.method.idempotent", "true");

            return config;
        }
    }

    @Test
    void beansAreProduced() {
        // Verify all three beans are produced by the CDI producer
        assertThat(defaultActionQosConfig).isNotNull();
        assertThat(namedActionQosConfig).isNotNull();
        assertThat(actionQosPropertyProvider).isNotNull();
    }

    @Test
    void namedActionQosConfig_containsExpectedEntries() {
        // Verify the producer correctly scans and creates named configurations
        assertThat(namedActionQosConfig.size()).isEqualTo(2);
        assertThat(namedActionQosConfig.contains("fast-retry")).isTrue();
        assertThat(namedActionQosConfig.contains("agents.agent.method")).isTrue();
    }

    @Test
    void actionQosPropertyProvider_resolvesSimpleName() {
        // When - resolve simple name
        var resolved = actionQosPropertyProvider.getBound("fast-retry");

        // Then - returns correct configuration
        assertThat(resolved).isNotNull();
        assertThat(resolved.getMaxAttempts()).isEqualTo(5);
        assertThat(resolved.getBackoffMillis()).isEqualTo(100L);
    }

    @Test
    void actionQosPropertyProvider_resolvesSimpleNameWithDollarBraces() {
        // When - resolve name with ${...} syntax
        var resolved = actionQosPropertyProvider.getBound("${fast-retry}");

        // Then - returns correct configuration (same as without braces)
        assertThat(resolved).isNotNull();
        assertThat(resolved.getMaxAttempts()).isEqualTo(5);
        assertThat(resolved.getBackoffMillis()).isEqualTo(100L);
    }

    @Test
    void actionQosPropertyProvider_resolvesHierarchicalName() {
        // When - resolve hierarchical name
        var resolved = actionQosPropertyProvider.getBound("agents.agent.method");

        // Then - returns correct configuration
        assertThat(resolved).isNotNull();
        assertThat(resolved.getMaxAttempts()).isEqualTo(10);
        assertThat(resolved.getIdempotent()).isTrue();
    }

    @Test
    void actionQosPropertyProvider_missingName_returnsNull() {
        // When - resolve missing name
        var resolved = actionQosPropertyProvider.getBound("nonexistent");

        // Then - returns null (fallback to defaults)
        assertThat(resolved).isNull();
    }

    @Test
    void actionQosPropertyProvider_blankExpression_returnsNull() {
        // When - resolve blank expression
        var resolved = actionQosPropertyProvider.getBound("");

        // Then - returns null (no override)
        assertThat(resolved).isNull();
    }

    @Test
    void actionQosPropertyProvider_nullExpression_returnsNull() {
        // When - resolve null expression
        var resolved = actionQosPropertyProvider.getBound(null);

        // Then - returns null (no override)
        assertThat(resolved).isNull();
    }
}