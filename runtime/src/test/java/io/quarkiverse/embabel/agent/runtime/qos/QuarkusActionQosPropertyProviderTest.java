package io.quarkiverse.embabel.agent.runtime.qos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.embabel.agent.spi.config.spring.AgentPlatformProperties;

import io.quarkiverse.embabel.agent.runtime.config.NamedActionQosConfig;

/**
 * Unit tests for {@link QuarkusActionQosPropertyProvider}.
 * <p>
 * Tests verify:
 * <ul>
 * <li>Normalization: {@code "${name}"} and {@code "name"} resolve to same key</li>
 * <li>Lookup: Successful and missing name scenarios</li>
 * <li>Partial overrides: Only configured fields are set</li>
 * <li>Hierarchical names: {@code "agents.agent.method"} lookups</li>
 * </ul>
 */
class QuarkusActionQosPropertyProviderTest {

    private NamedActionQosConfig namedConfig;
    private QuarkusActionQosPropertyProvider provider;

    @BeforeEach
    void setUp() {
        namedConfig = new NamedActionQosConfig();

        namedConfig.put("fast-retry",
                new AgentPlatformProperties.ActionQosProperties.ActionProperties(5, 100L, 1.5, 5000L, true));

        namedConfig.put("agents.agent.method",
                new AgentPlatformProperties.ActionQosProperties.ActionProperties(2, 500L, 3.0, 10000L, false));

        AgentPlatformProperties.ActionQosProperties.ActionProperties partial = new AgentPlatformProperties.ActionQosProperties.ActionProperties();
        partial.setMaxAttempts(7);
        partial.setIdempotent(true);
        namedConfig.put("partial", partial);

        provider = new QuarkusActionQosPropertyProvider(namedConfig);
    }

    @Test
    void getBound_blankExpression_returnsNull() {
        assertThat(provider.getBound("")).isNull();
        assertThat(provider.getBound("   ")).isNull();
        assertThat(provider.getBound(null)).isNull();
    }

    @Test
    void getBound_rawName_lookupsCorrectly() {
        var result = provider.getBound("fast-retry");

        assertThat(result).isNotNull();
        assertThat(result.getMaxAttempts()).isEqualTo(5);
        assertThat(result.getBackoffMillis()).isEqualTo(100L);
        assertThat(result.getBackoffMultiplier()).isEqualTo(1.5);
        assertThat(result.getBackoffMaxInterval()).isEqualTo(5000L);
        assertThat(result.getIdempotent()).isTrue();
    }

    @Test
    void getBound_expressionWithWrapper_lookupsCorrectly() {
        var result = provider.getBound("${fast-retry}");

        assertThat(result).isNotNull();
        assertThat(result.getMaxAttempts()).isEqualTo(5);
        assertThat(result.getBackoffMillis()).isEqualTo(100L);
        assertThat(result.getBackoffMultiplier()).isEqualTo(1.5);
        assertThat(result.getBackoffMaxInterval()).isEqualTo(5000L);
        assertThat(result.getIdempotent()).isTrue();
    }

    @Test
    void getBound_hierarchicalName_lookupsCorrectly() {
        var result = provider.getBound("agents.agent.method");

        assertThat(result).isNotNull();
        assertThat(result.getMaxAttempts()).isEqualTo(2);
        assertThat(result.getBackoffMillis()).isEqualTo(500L);
        assertThat(result.getBackoffMultiplier()).isEqualTo(3.0);
        assertThat(result.getBackoffMaxInterval()).isEqualTo(10000L);
        assertThat(result.getIdempotent()).isFalse();
    }

    @Test
    void getBound_hierarchicalNameWithWrapper_lookupsCorrectly() {
        var result = provider.getBound("${agents.agent.method}");

        assertThat(result).isNotNull();
        assertThat(result.getMaxAttempts()).isEqualTo(2);
        assertThat(result.getBackoffMillis()).isEqualTo(500L);
    }

    @Test
    void getBound_missingName_returnsNull() {
        assertThat(provider.getBound("nonexistent")).isNull();
    }

    @Test
    void getBound_missingNameWithWrapper_returnsNull() {
        assertThat(provider.getBound("${nonexistent}")).isNull();
    }

    @Test
    void getBound_partialConfig_onlyConfiguredFieldsSet() {
        var result = provider.getBound("partial");

        assertThat(result).isNotNull();
        assertThat(result.getMaxAttempts()).isEqualTo(7);
        assertThat(result.getIdempotent()).isTrue();
        assertThat(result.getBackoffMillis()).isNull();
        assertThat(result.getBackoffMultiplier()).isNull();
        assertThat(result.getBackoffMaxInterval()).isNull();
    }

    @Test
    void normalizeExpression_rawName_returnsSame() {
        assertThat(provider.normalizeExpression("fast-retry")).isEqualTo("fast-retry");
    }

    @Test
    void normalizeExpression_withWrapper_removesWrapper() {
        assertThat(provider.normalizeExpression("${fast-retry}")).isEqualTo("fast-retry");
    }

    @Test
    void normalizeExpression_hierarchicalRaw_returnsSame() {
        assertThat(provider.normalizeExpression("agents.agent.method")).isEqualTo("agents.agent.method");
    }

    @Test
    void normalizeExpression_hierarchicalWithWrapper_removesWrapper() {
        assertThat(provider.normalizeExpression("${agents.agent.method}")).isEqualTo("agents.agent.method");
    }

    @Test
    void normalizeExpression_withWhitespace_trimsCorrectly() {
        assertThat(provider.normalizeExpression("  fast-retry  ")).isEqualTo("fast-retry");
        assertThat(provider.normalizeExpression("${  fast-retry  }")).isEqualTo("fast-retry");
        assertThat(provider.normalizeExpression("  ${fast-retry}  ")).isEqualTo("fast-retry");
    }

    @Test
    void normalizeExpression_malformedWrapper_treatsAsRawName() {
        assertThat(provider.normalizeExpression("${incomplete")).isEqualTo("${incomplete");
        assertThat(provider.normalizeExpression("incomplete}")).isEqualTo("incomplete}");
        assertThat(provider.normalizeExpression("{no-dollar}")).isEqualTo("{no-dollar}");
    }

    @Test
    void getBound_returnsCorrectType() {
        var result = provider.getBound("fast-retry");

        assertThat(result).isInstanceOf(
                AgentPlatformProperties.ActionQosProperties.ActionProperties.class);
    }

    @Test
    void getBound_multipleLookupsForSameName_returnConsistentResults() {
        var result1 = provider.getBound("fast-retry");
        var result2 = provider.getBound("${fast-retry}");

        assertThat(result1.getMaxAttempts()).isEqualTo(result2.getMaxAttempts());
        assertThat(result1.getBackoffMillis()).isEqualTo(result2.getBackoffMillis());
        assertThat(result1.getBackoffMultiplier()).isEqualTo(result2.getBackoffMultiplier());
        assertThat(result1.getBackoffMaxInterval()).isEqualTo(result2.getBackoffMaxInterval());
        assertThat(result1.getIdempotent()).isEqualTo(result2.getIdempotent());
    }

    @Test
    void getBound_emptyConfig_returnsNonNullButEmptyProperties() {
        namedConfig.put("empty", new AgentPlatformProperties.ActionQosProperties.ActionProperties());

        var result = provider.getBound("empty");

        assertThat(result).isNotNull();
        assertThat(result.getMaxAttempts()).isNull();
        assertThat(result.getBackoffMillis()).isNull();
        assertThat(result.getBackoffMultiplier()).isNull();
        assertThat(result.getBackoffMaxInterval()).isNull();
        assertThat(result.getIdempotent()).isNull();
    }
}
