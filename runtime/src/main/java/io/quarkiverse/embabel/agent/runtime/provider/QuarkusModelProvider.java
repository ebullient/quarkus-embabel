package io.quarkiverse.embabel.agent.runtime.provider;

import java.lang.annotation.Annotation;
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

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.embabel.agent.runtime.embedding.QuarkusEmbeddingService;
import io.quarkiverse.langchain4j.ModelName;

/**
 * Quarkus implementation of {@link ModelProvider} that discovers LLM and embedding services
 * via CDI and delegates selection logic to Embabel's {@link ConfigurableModelProvider}.
 * <p>
 * This provider automatically discovers all {@link LlmService} and {@link EmbeddingService}
 * beans created by the extension's build-time bean generation (Step 12). It then uses
 * Embabel's standard configuration-based selection logic to choose the appropriate model
 * based on criteria like role or name.
 * <p>
 * <b>Architecture</b>:
 * <ol>
 * <li>Build-time: Extension creates {@link io.quarkiverse.embabel.agent.runtime.service.QuarkusLlmService}
 * beans for each configured ChatModel</li>
 * <li>Runtime: This provider discovers all LlmService beans via CDI {@link Instance}</li>
 * <li>Selection: Delegates to {@link ConfigurableModelProvider} for model selection logic</li>
 * </ol>
 * <p>
 * <b>Configuration Example</b>:
 *
 * <pre>
 * # Quarkus LangChain4j creates ChatModel beans
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
    Instance<EmbeddingModel> embeddingModels;

    @Inject
    ConfigurableModelProviderProperties properties;

    private ConfigurableModelProvider delegate;

    /**
     * Initializes the provider by discovering all LLM and embedding service beans
     * and creating the delegate {@link ConfigurableModelProvider}.
     * <p>
     * This method is called automatically by CDI after dependency injection.
     */
    @PostConstruct
    void init() {
        // Collect all LlmService beans (created by build-time bean generation)
        List<LlmService> llmList = new ArrayList<>();
        llmServices.forEach(llmList::add);

        // Collect all EmbeddingService beans and wrap EmbeddingModel beans
        List<EmbeddingService> embeddingList = new ArrayList<>();
        embeddingServices.forEach(embeddingList::add);

        // Wrap any EmbeddingModel beans from quarkus-langchain4j
        for (Instance.Handle<EmbeddingModel> handle : embeddingModels.handles()) {
            EmbeddingModel model = handle.get();

            // Extract model name from @ModelName qualifier, default to "unknown"
            String modelName = "unknown";
            for (Annotation qualifier : handle.getBean().getQualifiers()) {
                if (qualifier.annotationType().equals(ModelName.class)) {
                    try {
                        modelName = (String) qualifier.annotationType().getMethod("value").invoke(qualifier);
                    } catch (Exception e) {
                        // Ignore and use default
                    }
                }
            }

            // Infer provider from package name (e.g., dev.langchain4j.model.openai.* -> "openai")
            String provider = "unknown";
            String beanClassName = handle.getBean().getBeanClass().getName();
            if (beanClassName.startsWith("dev.langchain4j.model.")) {
                String afterModel = beanClassName.substring("dev.langchain4j.model.".length());
                int dotIndex = afterModel.indexOf('.');
                if (dotIndex > 0) {
                    provider = afterModel.substring(0, dotIndex);
                } else {
                    provider = handle.getBean().getBeanClass().getSimpleName()
                            .replace("EmbeddingModel", "")
                            .toLowerCase();
                }
            }

            embeddingList.add(new QuarkusEmbeddingService(modelName, provider, model));
        }

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