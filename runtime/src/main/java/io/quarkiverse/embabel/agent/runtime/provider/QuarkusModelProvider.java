package io.quarkiverse.embabel.agent.runtime.provider;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    Instance<LlmService<?>> llmServices;

    @Inject
    Instance<EmbeddingService> embeddingServices;

    @Inject
    Instance<EmbeddingModel> embeddingModels;

    private ConfigurableModelProviderProperties properties;
    private ConfigurableModelProvider delegate;

    private List<LlmService<?>> llmList;
    private List<EmbeddingService> embeddingList;

    /**
     * Default constructor for CDI.
     * Properties will be injected or set via {@link #setProperties(ConfigurableModelProviderProperties)}.
     */
    public QuarkusModelProvider() {
        // CDI requires no-arg constructor
    }

    /**
     * Constructor for testing with explicit parameters.
     * This allows tests to create instances without CDI when Step 18 (configuration) is blocked.
     *
     * @param llmServices the list of LLM services
     * @param embeddingServices the list of embedding services
     * @param properties the configuration properties
     */
    public QuarkusModelProvider(
            List<LlmService<?>> llmServices,
            List<EmbeddingService> embeddingServices,
            ConfigurableModelProviderProperties properties) {
        this.llmList = new ArrayList<>(llmServices);
        this.embeddingList = new ArrayList<>(embeddingServices);
        this.properties = Objects.requireNonNull(properties, "Properties cannot be null");
        initDelegate();
    }

    /**
     * Sets the configuration properties.
     * This method is provided for testing when CDI injection is not available.
     *
     * @param properties the configuration properties
     */
    public void setProperties(ConfigurableModelProviderProperties properties) {
        this.properties = Objects.requireNonNull(properties, "Properties cannot be null");
    }

    /**
     * Initializes the provider by discovering all LLM and embedding service beans
     * and creating the delegate {@link ConfigurableModelProvider}.
     * <p>
     * This method is called automatically by CDI after dependency injection.
     */
    @PostConstruct
    void init() {
        if (properties == null) {
            throw new IllegalStateException(
                    "Properties not set. Either inject ConfigurableModelProviderProperties or call setProperties()");
        }

        // Collect all LlmService beans (created by Step 12's build step)
        llmList = new ArrayList<>();
        llmServices.forEach(llmList::add);

        // Collect all EmbeddingService beans and create wrappers for EmbeddingModel beans
        embeddingList = new ArrayList<>();

        // First, add any existing EmbeddingService beans
        embeddingServices.forEach(embeddingList::add);

        // Then, wrap any EmbeddingModel beans that don't have corresponding EmbeddingService beans
        // This allows quarkus-langchain4j EmbeddingModel beans to be used directly
        for (Instance.Handle<EmbeddingModel> handle : embeddingModels.handles()) {
            EmbeddingModel model = handle.get();

            // Determine the model name and provider from qualifiers or use defaults
            String modelName = "unknown";
            String provider = "unknown";

            // Check for @ModelName qualifier
            for (Annotation qualifier : handle.getBean().getQualifiers()) {
                if (qualifier.annotationType().equals(ModelName.class)) {
                    try {
                        modelName = (String) qualifier.annotationType().getMethod("value").invoke(qualifier);
                    } catch (Exception e) {
                        // Ignore and use default
                    }
                }
            }

            // Infer provider from package name (e.g., dev.langchain4j.model.openai.OpenAiEmbeddingModel -> openai)
            // This is used for display/metadata purposes only, not for routing
            String beanClassName = handle.getBean().getBeanClass().getName();
            if (beanClassName.startsWith("dev.langchain4j.model.")) {
                String afterModel = beanClassName.substring("dev.langchain4j.model.".length());
                int dotIndex = afterModel.indexOf('.');
                if (dotIndex > 0) {
                    provider = afterModel.substring(0, dotIndex);
                }
            }

            // If we couldn't extract from package, use the bean class simple name
            if (provider.equals("unknown")) {
                provider = handle.getBean().getBeanClass().getSimpleName()
                        .replace("EmbeddingModel", "")
                        .toLowerCase();
            }

            // Create QuarkusEmbeddingService wrapper
            embeddingList.add(new QuarkusEmbeddingService(modelName, provider, model));
        }

        initDelegate();
    }

    /**
     * Initializes the delegate ConfigurableModelProvider.
     */
    private void initDelegate() {
        // Create delegate that handles all selection logic
        delegate = new ConfigurableModelProvider(llmList, embeddingList, properties);
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
            throw new IllegalStateException("Provider not initialized. Call init() or use CDI.");
        }
    }

    /**
     * Gets the list of discovered LLM services.
     * Useful for testing and diagnostics.
     *
     * @return the list of LLM services
     */
    public List<LlmService<?>> getLlmServices() {
        return llmList != null ? new ArrayList<>(llmList) : new ArrayList<>();
    }

    /**
     * Gets the list of discovered embedding services.
     * Useful for testing and diagnostics.
     *
     * @return the list of embedding services
     */
    public List<EmbeddingService> getEmbeddingServices() {
        return embeddingList != null ? new ArrayList<>(embeddingList) : new ArrayList<>();
    }
}