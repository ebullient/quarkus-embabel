package io.quarkiverse.embabel.agent.runtime.qos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.embabel.agent.spi.config.spring.AgentPlatformProperties;

import io.quarkiverse.embabel.agent.runtime.config.ActionQosConfig;
import io.quarkiverse.embabel.agent.runtime.config.NamedActionQosConfig;

/**
 * Unit tests for {@link QuarkusActionQosPropertyProvider}.
 * <p>
 * Tests verify:
 * <ul>
 * <li>Normalization: {@code "${name}"} and {@code "name"} resolve to same key</li>
 * <li>Lookup: Successful and missing name scenarios</li>
 * <li>Conversion: ActionQosConfig → ActionProperties field mapping</li>
 * <li>Partial overrides: Only configured fields are set</li>
 * <li>Hierarchical names: {@code "agents.agent.method"} lookups</li>
 * </ul>
 * <p>
 * These tests focus on normalization and lookup contract behavior, not string manipulation internals.
 */
class QuarkusActionQosPropertyProviderTest {

    private NamedActionQosConfig namedConfig;
    private QuarkusActionQosPropertyProvider provider;

    @BeforeEach
    void setUp() {
        namedConfig = new NamedActionQosConfig();

        // Add test configurations
        // Simple name - complete config
        ActionQosConfig fastRetry = new ActionQosConfig(5, 100L, 1.5, 5000L, true);
        namedConfig.put("fast-retry", fastRetry);

        // Hierarchical name - complete config
        ActionQosConfig hierarchical = new ActionQosConfig(2, 500L, 3.0, 10000L, false);
        namedConfig.put("agents.agent.method", hierarchical);

        // Partial config - only some fields
        ActionQosConfig partial = new ActionQosConfig();
        partial.setMaxAttempts(7);
        partial.setIdempotent(true);
        namedConfig.put("partial", partial);

        provider = new QuarkusActionQosPropertyProvider(namedConfig);
    }

    @Test
    void getBound_blankExpression_returnsNull() {
        // When - blank input
        assertThat(provider.getBound("")).isNull();
        assertThat(provider.getBound("   ")).isNull();
        assertThat(provider.getBound(null)).isNull();
    }

    @Test
    void getBound_rawName_lookupsCorrectly() {
        // When - raw name input (no ${...} wrapper)
        var result = provider.getBound("fast-retry");

        // Then - successful lookup
        assertThat(result).isNotNull();
        assertThat(result.getMaxAttempts()).isEqualTo(5);
        assertThat(result.getBackoffMillis()).isEqualTo(100L);
        assertThat(result.getBackoffMultiplier()).isEqualTo(1.5);
        assertThat(result.getBackoffMaxInterval()).isEqualTo(5000L);
        assertThat(result.getIdempotent()).isTrue();
    }

    @Test
    void getBound_expressionWithWrapper_lookupsCorrectly() {
        // When - expression with ${...} wrapper
        var result = provider.getBound("${fast-retry}");

        // Then - successful lookup (same as raw name)
        assertThat(result).isNotNull();
        assertThat(result.getMaxAttempts()).isEqualTo(5);
        assertThat(result.getBackoffMillis()).isEqualTo(100L);
        assertThat(result.getBackoffMultiplier()).isEqualTo(1.5);
        assertThat(result.getBackoffMaxInterval()).isEqualTo(5000L);
        assertThat(result.getIdempotent()).isTrue();
    }

    @Test
    void getBound_hierarchicalName_lookupsCorrectly() {
        // When - hierarchical name (agents.agent.method)
        var result = provider.getBound("agents.agent.method");

        // Then - successful lookup
        assertThat(result).isNotNull();
        assertThat(result.getMaxAttempts()).isEqualTo(2);
        assertThat(result.getBackoffMillis()).isEqualTo(500L);
        assertThat(result.getBackoffMultiplier()).isEqualTo(3.0);
        assertThat(result.getBackoffMaxInterval()).isEqualTo(10000L);
        assertThat(result.getIdempotent()).isFalse();
    }

    @Test
    void getBound_hierarchicalNameWithWrapper_lookupsCorrectly() {
        // When - hierarchical name with ${...} wrapper
        var result = provider.getBound("${agents.agent.method}");

        // Then - successful lookup (same as raw hierarchical name)
        assertThat(result).isNotNull();
        assertThat(result.getMaxAttempts()).isEqualTo(2);
        assertThat(result.getBackoffMillis()).isEqualTo(500L);
    }

    @Test
    void getBound_missingName_returnsNull() {
        // When - name not found in config
        var result = provider.getBound("nonexistent");

        // Then - returns null (fallback to defaults)
        assertThat(result).isNull();
    }

    @Test
    void getBound_missingNameWithWrapper_returnsNull() {
        // When - name not found, even with ${...} wrapper
        var result = provider.getBound("${nonexistent}");

        // Then - returns null
        assertThat(result).isNull();
    }

    @Test
    void getBound_partialConfig_onlyConfiguredFieldsSet() {
        // When - lookup partial config (only maxAttempts and idempotent set)
        var result = provider.getBound("partial");

        // Then - only configured fields are present
        assertThat(result).isNotNull();
        assertThat(result.getMaxAttempts()).isEqualTo(7);
        assertThat(result.getIdempotent()).isTrue();

        // Unconfigured fields are null (will merge with defaults in DefaultActionQosProvider)
        assertThat(result.getBackoffMillis()).isNull();
        assertThat(result.getBackoffMultiplier()).isNull();
        assertThat(result.getBackoffMaxInterval()).isNull();
    }

    @Test
    void normalizeExpression_rawName_returnsSame() {
        // When - raw name (no wrapper)
        String normalized = provider.normalizeExpression("fast-retry");

        // Then - returns same value
        assertThat(normalized).isEqualTo("fast-retry");
    }

    @Test
    void normalizeExpression_withWrapper_removesWrapper() {
        // When - expression with ${...} wrapper
        String normalized = provider.normalizeExpression("${fast-retry}");

        // Then - wrapper removed
        assertThat(normalized).isEqualTo("fast-retry");
    }

    @Test
    void normalizeExpression_hierarchicalRaw_returnsSame() {
        // When - hierarchical raw name
        String normalized = provider.normalizeExpression("agents.agent.method");

        // Then - returns same value
        assertThat(normalized).isEqualTo("agents.agent.method");
    }

    @Test
    void normalizeExpression_hierarchicalWithWrapper_removesWrapper() {
        // When - hierarchical with ${...} wrapper
        String normalized = provider.normalizeExpression("${agents.agent.method}");

        // Then - wrapper removed
        assertThat(normalized).isEqualTo("agents.agent.method");
    }

    @Test
    void normalizeExpression_withWhitespace_trimsCorrectly() {
        // When - expressions with whitespace
        assertThat(provider.normalizeExpression("  fast-retry  ")).isEqualTo("fast-retry");
        assertThat(provider.normalizeExpression("${  fast-retry  }")).isEqualTo("fast-retry");
        assertThat(provider.normalizeExpression("  ${fast-retry}  ")).isEqualTo("fast-retry");
    }

    @Test
    void normalizeExpression_malformedWrapper_treatsAsRawName() {
        // When - malformed ${...} patterns
        // These are edge cases - treated as raw names, not normalized
        assertThat(provider.normalizeExpression("${incomplete")).isEqualTo("${incomplete");
        assertThat(provider.normalizeExpression("incomplete}")).isEqualTo("incomplete}");
        assertThat(provider.normalizeExpression("{no-dollar}")).isEqualTo("{no-dollar}");
    }

    @Test
    void getBound_returnsCorrectType() {
        // When - successful lookup
        var result = provider.getBound("fast-retry");

        // Then - returns upstream ActionProperties type
        assertThat(result).isInstanceOf(
                AgentPlatformProperties.ActionQosProperties.ActionProperties.class);
    }

    @Test
    void getBound_multipleLookupsForSameName_returnConsistentResults() {
        // When - multiple lookups for the same name
        var result1 = provider.getBound("fast-retry");
        var result2 = provider.getBound("${fast-retry}");

        // Then - both return equivalent results
        assertThat(result1.getMaxAttempts()).isEqualTo(result2.getMaxAttempts());
        assertThat(result1.getBackoffMillis()).isEqualTo(result2.getBackoffMillis());
        assertThat(result1.getBackoffMultiplier()).isEqualTo(result2.getBackoffMultiplier());
        assertThat(result1.getBackoffMaxInterval()).isEqualTo(result2.getBackoffMaxInterval());
        assertThat(result1.getIdempotent()).isEqualTo(result2.getIdempotent());
    }

    @Test
    void getBound_emptyConfig_returnsNonNullButEmptyProperties() {
        // Given - add empty config entry
        namedConfig.put("empty", new ActionQosConfig());

        // When - lookup empty config
        var result = provider.getBound("empty");

        // Then - returns ActionProperties instance with all null fields
        assertThat(result).isNotNull();
        assertThat(result.getMaxAttempts()).isNull();
        assertThat(result.getBackoffMillis()).isNull();
        assertThat(result.getBackoffMultiplier()).isNull();
        assertThat(result.getBackoffMaxInterval()).isNull();
        assertThat(result.getIdempotent()).isNull();
    }
}
