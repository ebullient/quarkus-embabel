package io.quarkiverse.embabel.agent.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.embabel.agent.api.tool.config.ToolLoopConfiguration;
import com.embabel.common.ai.model.ConfigurableModelProviderProperties;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Tests that Embabel configuration classes are correctly bound from application properties
 * via the {@code quarkus-spring-boot-properties} extension.
 * <p>
 * This test validates that:
 * <ul>
 * <li>{@link ToolLoopConfiguration} binds nested properties (type, iterations, parallel config, error handling)</li>
 * <li>{@link ConfigurableModelProviderProperties} binds maps and default values for models</li>
 * <li>Complex types (Duration, Enum, Map) are correctly converted</li>
 * </ul>
 * <p>
 * The quarkus-spring-boot-properties extension allows us to inject Embabel's {@code @ConfigurationProperties}
 * classes directly without reimplementing them as Quarkus config mappings.
 *
 * @see ToolLoopConfiguration
 * @see ConfigurableModelProviderProperties
 */
@QuarkusTest
@TestProfile(EmbabelConfigBindingTest.ConfigProfile.class)
class EmbabelConfigBindingTest {

    @Inject
    ToolLoopConfiguration toolLoopConfig;

    @Inject
    ConfigurableModelProviderProperties modelProviderProps;

    /**
     * Verifies that ToolLoopConfiguration binds all nested properties correctly:
     * - Enum type (ToolLoopType.PARALLEL)
     * - Primitive fields (maxIterations)
     * - Nested object properties (parallel, toolNotFound, emptyResponse)
     * - Duration conversion (30s → Duration.ofSeconds(30))
     */
    @Test
    void shouldBindToolLoopConfiguration() {
        assertThat(toolLoopConfig).isNotNull();

        // Basic properties
        assertThat(toolLoopConfig.getType())
                .as("Tool loop type should be PARALLEL")
                .isEqualTo(ToolLoopConfiguration.ToolLoopType.PARALLEL);
        assertThat(toolLoopConfig.getMaxIterations())
                .as("Max iterations should be 3")
                .isEqualTo(3);

        // Parallel mode properties
        assertThat(toolLoopConfig.getParallel()).isNotNull();
        assertThat(toolLoopConfig.getParallel().getPerToolTimeout())
                .as("Per-tool timeout should be 20 seconds")
                .isEqualTo(Duration.ofSeconds(20));
        assertThat(toolLoopConfig.getParallel().getBatchTimeout())
                .as("Batch timeout should be 45 seconds")
                .isEqualTo(Duration.ofSeconds(45));

        // Tool not found properties
        assertThat(toolLoopConfig.getToolNotFound()).isNotNull();
        assertThat(toolLoopConfig.getToolNotFound().getMaxRetries())
                .as("Tool not found max retries should be 5")
                .isEqualTo(5);
        assertThat(toolLoopConfig.getToolNotFound().getMinFuzzyLength())
                .as("Tool not found min fuzzy length should be 4")
                .isEqualTo(4);

        // Empty response properties
        assertThat(toolLoopConfig.getEmptyResponse()).isNotNull();
        assertThat(toolLoopConfig.getEmptyResponse().getMaxRetries())
                .as("Empty response max retries should be 2")
                .isEqualTo(2);
        assertThat(toolLoopConfig.getEmptyResponse().getNudgeMessage())
                .as("Empty response nudge message should be customized")
                .isEqualTo("Please respond");
    }

    /**
     * Verifies that ConfigurableModelProviderProperties binds:
     * - Default values (defaultLlm, defaultEmbeddingModel)
     * - Map properties (llms, embeddingServices)
     * - Multiple map entries with different roles
     */
    @Test
    void shouldBindModelProviderProperties() {
        assertThat(modelProviderProps).isNotNull();

        // Default values
        assertThat(modelProviderProps.getDefaultLlm())
                .as("Default LLM should be gpt-4o")
                .isEqualTo("gpt-4o");
        assertThat(modelProviderProps.getDefaultEmbeddingModel())
                .as("Default embedding model should be text-embedding-3-small")
                .isEqualTo("text-embedding-3-small");

        // LLM role mappings
        assertThat(modelProviderProps.getLlms())
                .as("LLM roles should contain all configured mappings")
                .containsEntry("best", "gpt-4o")
                .containsEntry("fast", "gpt-4o-mini")
                .containsEntry("cheap", "gpt-3.5-turbo")
                .hasSize(3);

        // Embedding service role mappings
        assertThat(modelProviderProps.getEmbeddingServices())
                .as("Embedding service roles should contain all configured mappings")
                .containsEntry("best", "text-embedding-3-large")
                .containsEntry("fast", "text-embedding-3-small")
                .hasSize(2);
    }

    /**
     * Verifies that helper methods on ConfigurableModelProviderProperties work correctly
     * with bound properties.
     */
    @Test
    void shouldProvideWellKnownModelNames() {
        assertThat(modelProviderProps.allWellKnownLlmNames())
                .as("All well-known LLM names should include default + role mappings")
                .containsExactlyInAnyOrder("gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo");

        assertThat(modelProviderProps.allWellKnownEmbeddingServiceNames())
                .as("All well-known embedding service names should include default + role mappings")
                .containsExactlyInAnyOrder("text-embedding-3-small", "text-embedding-3-large");
    }

    /**
     * Verifies that ToolLoopConfiguration uses configured values instead of defaults.
     */
    @Test
    void toolLoopConfigurationShouldUseConfiguredValues() {
        // When properties are provided (as in this test), verify they override defaults
        assertThat(toolLoopConfig.getType())
                .as("Type should be overridden to PARALLEL")
                .isNotEqualTo(ToolLoopConfiguration.ToolLoopType.DEFAULT);

        // Verify defaults are not used when properties are set
        assertThat(toolLoopConfig.getMaxIterations())
                .as("Max iterations should be overridden (default is 20)")
                .isNotEqualTo(20);
    }

    /**
     * Test profile that provides Embabel configuration properties to verify binding.
     */
    public static class ConfigProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            return java.util.Map.ofEntries(
                    // ToolLoop Configuration
                    java.util.Map.entry("embabel.agent.platform.toolloop.type", "PARALLEL"),
                    java.util.Map.entry("embabel.agent.platform.toolloop.max-iterations", "3"),
                    java.util.Map.entry("embabel.agent.platform.toolloop.parallel.per-tool-timeout", "20s"),
                    java.util.Map.entry("embabel.agent.platform.toolloop.parallel.batch-timeout", "45s"),
                    java.util.Map.entry("embabel.agent.platform.toolloop.tool-not-found.max-retries", "5"),
                    java.util.Map.entry("embabel.agent.platform.toolloop.tool-not-found.min-fuzzy-length", "4"),
                    java.util.Map.entry("embabel.agent.platform.toolloop.empty-response.max-retries", "2"),
                    java.util.Map.entry("embabel.agent.platform.toolloop.empty-response.nudge-message", "Please respond"),
                    // Model Provider Configuration
                    java.util.Map.entry("embabel.models.default-llm", "gpt-4o"),
                    java.util.Map.entry("embabel.models.default-embedding-model", "text-embedding-3-small"),
                    java.util.Map.entry("embabel.models.llms.best", "gpt-4o"),
                    java.util.Map.entry("embabel.models.llms.fast", "gpt-4o-mini"),
                    java.util.Map.entry("embabel.models.llms.cheap", "gpt-3.5-turbo"),
                    java.util.Map.entry("embabel.models.embedding-services.best", "text-embedding-3-large"),
                    java.util.Map.entry("embabel.models.embedding-services.fast", "text-embedding-3-small"));
        }
    }
}
