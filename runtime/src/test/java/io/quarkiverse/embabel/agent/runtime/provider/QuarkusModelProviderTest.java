package io.quarkiverse.embabel.agent.runtime.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.embabel.agent.spi.LlmService;
import com.embabel.common.ai.model.ByNameModelSelectionCriteria;
import com.embabel.common.ai.model.ByRoleModelSelectionCriteria;
import com.embabel.common.ai.model.ConfigurableModelProviderProperties;
import com.embabel.common.ai.model.DefaultModelSelectionCriteria;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.NoSuitableModelException;

import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;

/**
 * Component tests for {@link QuarkusModelProvider} using CDI.
 * <p>
 * Uses {@link QuarkusComponentTest} for lightweight CDI testing with real
 * configuration injection and bean discovery.
 */
@QuarkusComponentTest
@TestConfigProperty(key = "embabel.models.default-llm", value = "gpt-4o")
class QuarkusModelProviderTest {

    @Inject
    QuarkusModelProvider provider;

    @Test
    void getLlm_withDefaultCriteria_returnsDefaultModel() {
        // When - uses configuration embabel.models.default-llm=gpt-4o
        LlmService<?> result = provider.getLlm(DefaultModelSelectionCriteria.INSTANCE);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("gpt-4o");
        assertThat(result.getProvider()).isEqualTo("openai");
    }

    @Test
    void getLlm_withByNameCriteria_returnsNamedModel() {
        // When
        LlmService<?> result = provider.getLlm(new ByNameModelSelectionCriteria("claude-3-5-sonnet"));

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("claude-3-5-sonnet");
        assertThat(result.getProvider()).isEqualTo("anthropic");
    }

    @Test
    @TestConfigProperty(key = "embabel.models.default-llm", value = "gpt-4o-mini")
    void getLlm_withByNameCriteria_fast_returnsCorrectModel() {
        // When
        LlmService<?> result = provider.getLlm(new ByNameModelSelectionCriteria("gpt-4o-mini"));

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("gpt-4o-mini");
        assertThat(result.getProvider()).isEqualTo("openai");
    }

    @Test
    @TestConfigProperty(key = "embabel.models.default-llm", value = "gpt-4o")
    @TestConfigProperty(key = "embabel.models.llms.best", value = "gpt-4o")
    @TestConfigProperty(key = "embabel.models.llms.fast", value = "gpt-4o-mini")
    void getLlm_withByRoleCriteria_returnsRoleMappedModel() {
        // When
        LlmService<?> bestModel = provider.getLlm(new ByRoleModelSelectionCriteria("best"));
        LlmService<?> fastModel = provider.getLlm(new ByRoleModelSelectionCriteria("fast"));

        // Then
        assertThat(bestModel.getName()).isEqualTo("gpt-4o");
        assertThat(fastModel.getName()).isEqualTo("gpt-4o-mini");
    }

    @Test
    @TestConfigProperty(key = "embabel.models.default-llm", value = "gpt-4o")
    void getLlm_withUnknownName_throwsException() {
        // When/Then
        assertThatThrownBy(() -> provider.getLlm(new ByNameModelSelectionCriteria("unknown-model")))
                .isInstanceOf(NoSuitableModelException.class);
    }

    @Test
    @TestConfigProperty(key = "embabel.models.default-llm", value = "gpt-4o")
    void getLlm_withUnknownRole_throwsException() {
        // When/Then
        assertThatThrownBy(() -> provider.getLlm(new ByRoleModelSelectionCriteria("unknown-role")))
                .isInstanceOf(NoSuitableModelException.class);
    }

    @Test
    @TestConfigProperty(key = "embabel.models.default-llm", value = "gpt-4o")
    void listModelNames_returnsAllModelNames() {
        // When
        var names = provider.listModelNames(LlmService.class);

        // Then
        assertThat(names).containsExactlyInAnyOrder("gpt-4o", "gpt-4o-mini", "claude-3-5-sonnet");
    }

    @Test
    @TestConfigProperty(key = "embabel.models.default-llm", value = "gpt-4o")
    @TestConfigProperty(key = "embabel.models.llms.best", value = "gpt-4o")
    @TestConfigProperty(key = "embabel.models.llms.fast", value = "gpt-4o-mini")
    void listRoles_returnsConfiguredRoles() {
        // When
        var roles = provider.listRoles(LlmService.class);

        // Then
        assertThat(roles).containsExactlyInAnyOrder("best", "fast");
    }

    @Test
    @TestConfigProperty(key = "embabel.models.default-llm", value = "gpt-4o")
    @TestConfigProperty(key = "embabel.models.default-embedding-model", value = "text-embedding-3-small")
    void listModels_returnsAllModelMetadata() {
        // When
        var models = provider.listModels();

        // Then - 3 LLMs + 1 embedding service from our producer
        // Note: There may be additional embedding services from auto-discovery in the runtime module
        assertThat(models.size()).isGreaterThanOrEqualTo(4);
        assertThat(models.stream().map(m -> m.getName()))
                .contains("gpt-4o", "gpt-4o-mini", "claude-3-5-sonnet", "text-embedding-3-small");
    }

    @Test
    @TestConfigProperty(key = "embabel.models.default-llm", value = "gpt-4o")
    void infoString_returnsFormattedInfo() {
        // When
        String info = provider.infoString(true, 0);

        // Then
        assertThat(info)
                .contains("Default LLM: gpt-4o")
                .contains("Available LLMs:")
                .contains("gpt-4o")
                .contains("gpt-4o-mini")
                .contains("claude-3-5-sonnet");
    }

    @Test
    @TestConfigProperty(key = "embabel.models.default-llm", value = "gpt-4o")
    @TestConfigProperty(key = "embabel.models.default-embedding-model", value = "text-embedding-3-small")
    void getEmbeddingService_withDefaultCriteria_returnsDefaultModel() {
        // When
        EmbeddingService result = provider.getEmbeddingService(DefaultModelSelectionCriteria.INSTANCE);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("text-embedding-3-small");
        assertThat(result.getProvider()).isEqualTo("openai");
    }

    /**
     * Produces mock LLM and embedding services as CDI beans for testing.
     */
    @ApplicationScoped
    static class MockServiceProducer {

        @Produces
        @ApplicationScoped
        LlmService gpt4Service() {
            LlmService mock = mock(LlmService.class);
            when(mock.getName()).thenReturn("gpt-4o");
            when(mock.getProvider()).thenReturn("openai");
            return mock;
        }

        @Produces
        @ApplicationScoped
        LlmService gpt4MiniService() {
            LlmService mock = mock(LlmService.class);
            when(mock.getName()).thenReturn("gpt-4o-mini");
            when(mock.getProvider()).thenReturn("openai");
            return mock;
        }

        @Produces
        @ApplicationScoped
        LlmService claudeService() {
            LlmService mock = mock(LlmService.class);
            when(mock.getName()).thenReturn("claude-3-5-sonnet");
            when(mock.getProvider()).thenReturn("anthropic");
            return mock;
        }

        @Produces
        @ApplicationScoped
        EmbeddingService embeddingService() {
            EmbeddingService mock = mock(EmbeddingService.class);
            when(mock.getName()).thenReturn("text-embedding-3-small");
            when(mock.getProvider()).thenReturn("openai");
            return mock;
        }

        @Produces
        @ApplicationScoped
        ConfigurableModelProviderProperties testProperties() {
            org.eclipse.microprofile.config.Config config = org.eclipse.microprofile.config.ConfigProvider.getConfig();
            String defaultLlm = config.getOptionalValue("embabel.models.default-llm", String.class).orElse(null);
            String defaultEmbedding = config.getOptionalValue("embabel.models.default-embedding-model", String.class)
                    .orElse(null);

            java.util.Map<String, String> llms = new java.util.HashMap<>();
            config.getPropertyNames().forEach(propName -> {
                if (propName.startsWith("embabel.models.llms.")) {
                    llms.put(propName.substring("embabel.models.llms.".length()), config.getValue(propName, String.class));
                }
            });

            return new ConfigurableModelProviderProperties(llms, java.util.Collections.emptyMap(), defaultLlm,
                    defaultEmbedding);
        }
    }
}
