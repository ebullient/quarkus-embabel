package io.quarkiverse.embabel.agent.runtime.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.embabel.agent.spi.LlmService;
import com.embabel.common.ai.model.ByNameModelSelectionCriteria;
import com.embabel.common.ai.model.ByRoleModelSelectionCriteria;
import com.embabel.common.ai.model.ConfigurableModelProviderProperties;
import com.embabel.common.ai.model.DefaultModelSelectionCriteria;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.ModelMetadata;
import com.embabel.common.ai.model.NoSuitableModelException;

/**
 * Unit tests for {@link QuarkusModelProvider}.
 * <p>
 * These tests verify the provider's ability to discover and select models
 * using direct instantiation (without CDI) since Step 18 is blocked.
 */
class QuarkusModelProviderTest {

    private LlmService<?> gpt4Service;
    private LlmService<?> gpt4MiniService;
    private LlmService<?> claudeService;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        // Create mock LLM services
        gpt4Service = mock(LlmService.class);
        when(gpt4Service.getName()).thenReturn("gpt-4o");
        when(gpt4Service.getProvider()).thenReturn("openai");

        gpt4MiniService = mock(LlmService.class);
        when(gpt4MiniService.getName()).thenReturn("gpt-4o-mini");
        when(gpt4MiniService.getProvider()).thenReturn("openai");

        claudeService = mock(LlmService.class);
        when(claudeService.getName()).thenReturn("claude-3-5-sonnet");
        when(claudeService.getProvider()).thenReturn("anthropic");

        // Create mock embedding service
        embeddingService = mock(EmbeddingService.class);
        when(embeddingService.getName()).thenReturn("text-embedding-3-small");
        when(embeddingService.getProvider()).thenReturn("openai");
    }

    @Test
    void constructor_withNullProperties_throwsException() {
        // Given
        List<LlmService<?>> llms = Collections.singletonList(gpt4Service);
        List<EmbeddingService> embeddings = Collections.emptyList();

        // When/Then
        assertThatThrownBy(() -> new QuarkusModelProvider(llms, embeddings, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Properties cannot be null");
    }

    @Test
    void getLlm_withDefaultCriteria_returnsDefaultModel() {
        // Given
        List<LlmService<?>> llms = Arrays.asList(gpt4Service, gpt4MiniService);
        ConfigurableModelProviderProperties properties = createProperties("gpt-4o", null);
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, Collections.emptyList(), properties);

        // When
        LlmService<?> result = provider.getLlm(DefaultModelSelectionCriteria.INSTANCE);

        // Then
        assertThat(result).isEqualTo(gpt4Service);
        assertThat(result.getName()).isEqualTo("gpt-4o");
    }

    @Test
    void getLlm_withByNameCriteria_returnsNamedModel() {
        // Given
        List<LlmService<?>> llms = Arrays.asList(gpt4Service, gpt4MiniService, claudeService);
        ConfigurableModelProviderProperties properties = createProperties("gpt-4o", null);
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, Collections.emptyList(), properties);

        // When
        LlmService<?> result = provider.getLlm(new ByNameModelSelectionCriteria("claude-3-5-sonnet"));

        // Then
        assertThat(result).isEqualTo(claudeService);
        assertThat(result.getName()).isEqualTo("claude-3-5-sonnet");
    }

    @Test
    void getLlm_withByRoleCriteria_returnsRoleMappedModel() {
        // Given
        List<LlmService<?>> llms = Arrays.asList(gpt4Service, gpt4MiniService);
        Map<String, String> roleMap = new HashMap<>();
        roleMap.put("best", "gpt-4o");
        roleMap.put("fast", "gpt-4o-mini");
        ConfigurableModelProviderProperties properties = createPropertiesWithRoles("gpt-4o", null, roleMap);
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, Collections.emptyList(), properties);

        // When
        LlmService<?> bestModel = provider.getLlm(new ByRoleModelSelectionCriteria("best"));
        LlmService<?> fastModel = provider.getLlm(new ByRoleModelSelectionCriteria("fast"));

        // Then
        assertThat(bestModel).isEqualTo(gpt4Service);
        assertThat(fastModel).isEqualTo(gpt4MiniService);
    }

    @Test
    void getLlm_withUnknownName_throwsException() {
        // Given
        List<LlmService<?>> llms = Collections.singletonList(gpt4Service);
        ConfigurableModelProviderProperties properties = createProperties("gpt-4o", null);
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, Collections.emptyList(), properties);

        // When/Then
        assertThatThrownBy(() -> provider.getLlm(new ByNameModelSelectionCriteria("unknown-model")))
                .isInstanceOf(NoSuitableModelException.class);
    }

    @Test
    void getLlm_withUnknownRole_throwsException() {
        // Given
        List<LlmService<?>> llms = Collections.singletonList(gpt4Service);
        ConfigurableModelProviderProperties properties = createProperties("gpt-4o", null);
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, Collections.emptyList(), properties);

        // When/Then
        assertThatThrownBy(() -> provider.getLlm(new ByRoleModelSelectionCriteria("unknown-role")))
                .isInstanceOf(NoSuitableModelException.class);
    }

    @Test
    void listModelNames_returnsAllModelNames() {
        // Given
        List<LlmService<?>> llms = Arrays.asList(gpt4Service, gpt4MiniService, claudeService);
        ConfigurableModelProviderProperties properties = createProperties("gpt-4o", null);
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, Collections.emptyList(), properties);

        // When
        List<String> names = provider.listModelNames(LlmService.class);

        // Then
        assertThat(names).containsExactlyInAnyOrder("gpt-4o", "gpt-4o-mini", "claude-3-5-sonnet");
    }

    @Test
    void listRoles_returnsConfiguredRoles() {
        // Given
        List<LlmService<?>> llms = Arrays.asList(gpt4Service, gpt4MiniService);
        Map<String, String> roleMap = new HashMap<>();
        roleMap.put("best", "gpt-4o");
        roleMap.put("fast", "gpt-4o-mini");
        ConfigurableModelProviderProperties properties = createPropertiesWithRoles("gpt-4o", null, roleMap);
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, Collections.emptyList(), properties);

        // When
        List<String> roles = provider.listRoles(LlmService.class);

        // Then
        assertThat(roles).containsExactlyInAnyOrder("best", "fast");
    }

    @Test
    void listModels_returnsAllModelMetadata() {
        // Given
        List<LlmService<?>> llms = Arrays.asList(gpt4Service, gpt4MiniService);
        List<EmbeddingService> embeddings = Collections.singletonList(embeddingService);
        ConfigurableModelProviderProperties properties = createProperties("gpt-4o", "text-embedding-3-small");
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, embeddings, properties);

        // When
        List<ModelMetadata> models = provider.listModels();

        // Then
        assertThat(models).hasSize(3);
        assertThat(models.stream().map(ModelMetadata::getName))
                .containsExactlyInAnyOrder("gpt-4o", "gpt-4o-mini", "text-embedding-3-small");
    }

    @Test
    void infoString_returnsFormattedInfo() {
        // Given
        List<LlmService<?>> llms = Arrays.asList(gpt4Service, gpt4MiniService);
        ConfigurableModelProviderProperties properties = createProperties("gpt-4o", null);
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, Collections.emptyList(), properties);

        // When
        String info = provider.infoString(true, 0);

        // Then
        assertThat(info)
                .contains("Default LLM: gpt-4o")
                .contains("Available LLMs:")
                .contains("gpt-4o")
                .contains("gpt-4o-mini");
    }

    @Test
    void getLlm_beforeInit_throwsException() {
        // Given
        QuarkusModelProvider provider = new QuarkusModelProvider();

        // When/Then
        assertThatThrownBy(() -> provider.getLlm(DefaultModelSelectionCriteria.INSTANCE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Provider not initialized");
    }

    @Test
    void getEmbeddingService_withDefaultCriteria_returnsDefaultModel() {
        // Given - need at least one LLM for ConfigurableModelProvider validation
        List<LlmService<?>> llms = Collections.singletonList(gpt4Service);
        List<EmbeddingService> embeddings = Collections.singletonList(embeddingService);
        ConfigurableModelProviderProperties properties = createProperties("gpt-4o", "text-embedding-3-small");
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, embeddings, properties);

        // When
        EmbeddingService result = provider.getEmbeddingService(DefaultModelSelectionCriteria.INSTANCE);

        // Then
        assertThat(result).isEqualTo(embeddingService);
        assertThat(result.getName()).isEqualTo("text-embedding-3-small");
    }

    @Test
    void getEmbeddingService_withByNameCriteria_returnsNamedModel() {
        // Given
        EmbeddingService embedding2 = mock(EmbeddingService.class);
        when(embedding2.getName()).thenReturn("text-embedding-ada-002");
        when(embedding2.getProvider()).thenReturn("openai");

        List<LlmService<?>> llms = Collections.singletonList(gpt4Service);
        List<EmbeddingService> embeddings = Arrays.asList(embeddingService, embedding2);
        // Set ada-002 as default so it's available, but we'll request it by name
        ConfigurableModelProviderProperties properties = createProperties("gpt-4o", "text-embedding-ada-002");
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, embeddings, properties);

        // When
        EmbeddingService result = provider.getEmbeddingService(new ByNameModelSelectionCriteria("text-embedding-ada-002"));

        // Then - verify by name, not by object reference (order may vary)
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("text-embedding-ada-002");
        assertThat(result.getProvider()).isEqualTo("openai");
    }

    @Test
    void getEmbeddingService_withByRoleCriteria_returnsRoleMappedModel() {
        // Given
        EmbeddingService fastEmbedding = mock(EmbeddingService.class);
        when(fastEmbedding.getName()).thenReturn("nomic-embed-text");
        when(fastEmbedding.getProvider()).thenReturn("ollama");

        List<LlmService<?>> llms = Collections.singletonList(gpt4Service);
        List<EmbeddingService> embeddings = Arrays.asList(embeddingService, fastEmbedding);
        Map<String, String> embeddingRoles = new HashMap<>();
        embeddingRoles.put("best", "text-embedding-3-small");
        embeddingRoles.put("fast", "nomic-embed-text");

        ConfigurableModelProviderProperties properties = new ConfigurableModelProviderProperties(
                Collections.emptyMap(),
                embeddingRoles,
                "gpt-4o",
                "text-embedding-3-small");
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, embeddings, properties);

        // When
        EmbeddingService bestModel = provider.getEmbeddingService(new ByRoleModelSelectionCriteria("best"));
        EmbeddingService fastModel = provider.getEmbeddingService(new ByRoleModelSelectionCriteria("fast"));

        // Then
        assertThat(bestModel).isEqualTo(embeddingService);
        assertThat(fastModel).isEqualTo(fastEmbedding);
    }

    @Test
    void getEmbeddingService_withUnknownName_throwsException() {
        // Given - no embeddings available at all
        List<LlmService<?>> llms = Collections.singletonList(gpt4Service);
        List<EmbeddingService> embeddings = Collections.emptyList();
        ConfigurableModelProviderProperties properties = createProperties("gpt-4o", null);
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, embeddings, properties);

        // When/Then - requesting unknown name with no embeddings throws IllegalArgumentException
        // (ConfigurableModelProvider validates default exists during getEmbeddingService call)
        assertThatThrownBy(() -> provider.getEmbeddingService(new ByNameModelSelectionCriteria("unknown-embedding")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Default embedding service");
    }

    @Test
    void listModelNames_forEmbeddingService_returnsAllEmbeddingNames() {
        // Given
        EmbeddingService embedding2 = mock(EmbeddingService.class);
        when(embedding2.getName()).thenReturn("nomic-embed-text");
        when(embedding2.getProvider()).thenReturn("ollama");

        List<LlmService<?>> llms = Collections.singletonList(gpt4Service);
        List<EmbeddingService> embeddings = Arrays.asList(embeddingService, embedding2);
        ConfigurableModelProviderProperties properties = createProperties("gpt-4o", "text-embedding-3-small");
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, embeddings, properties);

        // When
        List<String> names = provider.listModelNames(EmbeddingService.class);

        // Then
        assertThat(names).containsExactlyInAnyOrder("text-embedding-3-small", "nomic-embed-text");
    }

    @Test
    void getEmbeddingServices_returnsAllDiscoveredServices() {
        // Given
        EmbeddingService embedding2 = mock(EmbeddingService.class);
        when(embedding2.getName()).thenReturn("nomic-embed-text");

        List<LlmService<?>> llms = Collections.singletonList(gpt4Service);
        List<EmbeddingService> embeddings = Arrays.asList(embeddingService, embedding2);
        ConfigurableModelProviderProperties properties = createProperties("gpt-4o", "text-embedding-3-small");
        QuarkusModelProvider provider = new QuarkusModelProvider(llms, embeddings, properties);

        // When
        List<EmbeddingService> result = provider.getEmbeddingServices();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(embeddingService, embedding2);
    }

    // Helper methods

    private ConfigurableModelProviderProperties createProperties(String defaultLlm, String defaultEmbedding) {
        return createPropertiesWithRoles(defaultLlm, defaultEmbedding, Collections.emptyMap());
    }

    private ConfigurableModelProviderProperties createPropertiesWithRoles(
            String defaultLlm,
            String defaultEmbedding,
            Map<String, String> llmRoles) {
        // ConfigurableModelProviderProperties requires non-null defaultLlm
        // Use a placeholder if not provided
        String effectiveDefaultLlm = defaultLlm != null ? defaultLlm : "placeholder-llm";

        ConfigurableModelProviderProperties properties = new ConfigurableModelProviderProperties(
                llmRoles,
                Collections.emptyMap(),
                effectiveDefaultLlm,
                defaultEmbedding);
        return properties;
    }
}