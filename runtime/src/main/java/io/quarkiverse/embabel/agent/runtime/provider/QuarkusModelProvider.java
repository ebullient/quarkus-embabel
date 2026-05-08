package io.quarkiverse.embabel.agent.runtime.provider;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import com.embabel.agent.spi.LlmService;
import com.embabel.common.ai.model.ConfigurableModelProvider;
import com.embabel.common.ai.model.ConfigurableModelProviderProperties;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.ModelMetadata;
import com.embabel.common.ai.model.ModelProvider;
import com.embabel.common.ai.model.ModelSelectionCriteria;

import io.quarkiverse.embabel.agent.runtime.embedding.QuarkusEmbeddingService;

/**
 * Quarkus implementation of {@link ModelProvider} that discovers LLM and embedding services
 * via CDI and delegates selection logic to Embabel's {@link ConfigurableModelProvider}.
 * <p>
 * This provider automatically discovers all {@link LlmService} and {@link EmbeddingService}
 * beans and uses Embabel's standard configuration-based selection logic to choose the
 * appropriate model based on criteria like role or name.
 * <p>
 * <b>Architecture</b>:
 * <ol>
 * <li>Build-time: Extension creates {@link io.quarkiverse.embabel.agent.runtime.service.QuarkusLlmService}
 * and {@link QuarkusEmbeddingService} synthetic beans for each model configured via quarkus-langchain4j</li>
 * <li>Runtime: This provider discovers all LlmService and EmbeddingService beans via CDI {@link Instance}</li>
 * <li>Selection: Delegates to {@link ConfigurableModelProvider} for model selection logic</li>
 * </ol>
 * <p>
 * <b>Configuration Example</b>:
 *
 * <pre>
 * # Quarkus LangChain4j configuration creates ChatModel beans
 * quarkus.langchain4j.openai.chat-model.model-name=gpt-4o
 * quarkus.langchain4j.openai.fast.chat-model.model-name=gpt-4o-mini
 *
 * # Embabel maps logical names to models
 * embabel.models.default-llm=gpt-4o
 * embabel.models.llms.best=gpt-4o
 * embabel.models.llms.fast=gpt-4o-mini
 * </pre>
 *
 * @see ModelProvider
 * @see ConfigurableModelProvider
 * @see LlmService
 */
@ApplicationScoped
public class QuarkusModelProvider implements ModelProvider {

    @Inject
    Instance<LlmService> llmServices;

    @Inject
    Instance<EmbeddingService> embeddingServices;

    @Inject
    ConfigurableModelProviderProperties properties;

    private ConfigurableModelProvider delegate;

    /**
     * Initializes the provider by discovering all LLM and embedding service beans
     * and creating the delegate {@link ConfigurableModelProvider}.
     * <p>
     * This method is called automatically by CDI after dependency injection.
     * <p>
     * Both LlmService and EmbeddingService beans are created at build time by
     * {@link io.quarkiverse.embabel.agent.deployment.EmbabelProcessor} as synthetic beans.
     */
    @PostConstruct
    void init() {
        // Collect all LlmService beans (created by build-time synthetic bean registration)
        List<LlmService<?>> llmList = new ArrayList<>();
        llmServices.forEach(llmList::add);

        // Collect all EmbeddingService beans (created by build-time synthetic bean registration)
        List<EmbeddingService> embeddingList = new ArrayList<>();
        embeddingServices.forEach(embeddingList::add);

        // Create delegate that handles all selection logic
        delegate = new ConfigurableModelProvider((List) llmList, embeddingList, properties);
    }

    @Override
    public LlmService<?> getLlm(ModelSelectionCriteria criteria) {
        ensureInitialized();
        return delegate.getLlm(criteria);
    }

    @Override
    public EmbeddingService getEmbeddingService(ModelSelectionCriteria criteria) {
        ensureInitialized();
        return delegate.getEmbeddingService(criteria);
    }

    @Override
    public List<String> listRoles(Class<?> modelClass) {
        ensureInitialized();
        return delegate.listRoles(modelClass);
    }

    @Override
    public List<String> listModelNames(Class<?> modelClass) {
        ensureInitialized();
        return delegate.listModelNames(modelClass);
    }

    @Override
    public List<ModelMetadata> listModels() {
        ensureInitialized();
        return delegate.listModels();
    }

    @Override
    public String infoString(Boolean verbose, int indent) {
        ensureInitialized();
        return delegate.infoString(verbose, indent);
    }

    /**
     * Ensures the provider has been initialized.
     *
     * @throws IllegalStateException if not initialized
     */
    private void ensureInitialized() {
        if (delegate == null) {
            throw new IllegalStateException("Provider not initialized. CDI @PostConstruct has not run.");
        }
    }
}
