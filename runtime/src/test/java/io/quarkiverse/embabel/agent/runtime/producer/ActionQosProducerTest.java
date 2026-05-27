package io.quarkiverse.embabel.agent.runtime.producer;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.embabel.agent.runtime.config.ActionQosConfig;
import io.quarkiverse.embabel.agent.runtime.config.NamedActionQosConfig;
import io.quarkiverse.embabel.agent.runtime.qos.QuarkusActionQosPropertyProvider;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;

/**
 * Component tests for {@link ActionQosProducer} using CDI.
 * <p>
 * Tests verify that CDI beans are created and properly wired with sample
 * configuration properties containing default and named QoS entries.
 * <p>
 * These tests focus on CDI bean production and wiring, not on re-testing
 * the property parsing logic already covered in {@link io.quarkiverse.embabel.agent.runtime.config.ActionQosConfigBindingTest}.
 * <p>
 * Uses {@link QuarkusComponentTest} for lightweight CDI testing with real
 * bean discovery and injection.
 */
@QuarkusComponentTest
@TestConfigProperty(key = "embabel.agent.platform.action-qos.default.max-attempts", value = "3")
@TestConfigProperty(key = "embabel.agent.platform.action-qos.default.backoff-millis", value = "1000")
@TestConfigProperty(key = "embabel.agent.platform.action-qos.default.backoff-multiplier", value = "2.0")
@TestConfigProperty(key = "embabel.agent.platform.action-qos.default.backoff-max-interval", value = "30000")
@TestConfigProperty(key = "embabel.agent.platform.action-qos.default.idempotent", value = "false")
@TestConfigProperty(key = "embabel.agent.platform.action-qos.fast-retry.max-attempts", value = "5")
@TestConfigProperty(key = "embabel.agent.platform.action-qos.fast-retry.backoff-millis", value = "100")
@TestConfigProperty(key = "embabel.agent.platform.action-qos.agents.agent.method.max-attempts", value = "10")
@TestConfigProperty(key = "embabel.agent.platform.action-qos.agents.agent.method.idempotent", value = "true")
class ActionQosProducerTest {

    @Inject
    ActionQosProducer actionQosProducer;

    @Inject
    ActionQosConfig defaultActionQosConfig;

    @Inject
    NamedActionQosConfig namedActionQosConfig;

    @Inject
    QuarkusActionQosPropertyProvider actionQosPropertyProvider;

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