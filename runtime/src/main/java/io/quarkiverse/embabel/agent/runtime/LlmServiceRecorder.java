package io.quarkiverse.embabel.agent.runtime;

import java.util.function.Function;

import org.eclipse.microprofile.config.ConfigProvider;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.embabel.agent.runtime.embedding.QuarkusEmbeddingService;
import io.quarkiverse.embabel.agent.runtime.service.QuarkusLlmService;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Runtime recorder for creating LlmService and EmbeddingService instances.
 * <p>
 * This recorder is invoked at runtime initialization to create {@link QuarkusLlmService}
 * and {@link QuarkusEmbeddingService} instances for each configured model. It looks up
 * the appropriate model bean using CDI qualifiers and wraps it in the corresponding service.
 */
@Recorder
public class LlmServiceRecorder {

    /**
     * Creates a function that instantiates a QuarkusLlmService at runtime.
     * <p>
     * Uses CDI dependency injection to obtain the ChatModel bean from the creational context,
     * ensuring proper initialization order - CDI guarantees ChatModel is available before
     * creating the LlmService bean.
     *
     * @param configName the configuration name (e.g., "fast", "claude", or default)
     * @param provider the provider name (e.g., "openai", "anthropic", "ollama")
     * @return a function that creates the QuarkusLlmService instance
     */
    public Function<SyntheticCreationalContext<QuarkusLlmService>, QuarkusLlmService> createLlmService(String configName,
            String provider) {

        return context -> {
            // Extract actual model name from configuration at runtime
            // Config key pattern:
            // - Default: quarkus.langchain4j.{provider}.chat-model.model-name
            // - Named: quarkus.langchain4j.{provider}.{configName}.chat-model.model-name
            String modelNameKey;
            if (NamedConfigUtil.isDefault(configName)) {
                modelNameKey = "quarkus.langchain4j." + provider + ".chat-model.model-name";
            } else {
                modelNameKey = "quarkus.langchain4j." + provider + "." + configName + ".chat-model.model-name";
            }
            String modelName = ConfigProvider.getConfig()
                    .getOptionalValue(modelNameKey, String.class)
                    .orElse(configName); // Fallback to configName if model-name not configured

            ChatModel chatModel;
            StreamingChatModel streamingChatModel;
            if (NamedConfigUtil.isDefault(configName)) {
                chatModel = context.getInjectedReference(ChatModel.class);
                streamingChatModel = context.getInjectedReference(StreamingChatModel.class);
            } else {
                chatModel = context.getInjectedReference(ChatModel.class, ModelName.Literal.of(configName));
                streamingChatModel = context.getInjectedReference(StreamingChatModel.class,
                        ModelName.Literal.of(configName));
            }

            return new QuarkusLlmService(modelName, provider, chatModel, streamingChatModel);
        };
    }

    /**
     * Creates a function that instantiates a QuarkusEmbeddingService at runtime.
     * <p>
     * Uses CDI dependency injection to obtain the EmbeddingModel bean from the creational context,
     * ensuring proper initialization order - CDI guarantees EmbeddingModel is available before
     * creating the EmbeddingService bean.
     *
     * @param configName the configuration name (e.g., "fast", or default)
     * @param provider the provider name (e.g., "openai", "ollama")
     * @return a function that creates the QuarkusEmbeddingService instance
     */
    public Function<SyntheticCreationalContext<QuarkusEmbeddingService>, QuarkusEmbeddingService> createEmbeddingService(
            String configName,
            String provider) {

        return context -> {
            // Extract actual model name from configuration at runtime
            // Config key pattern:
            // - Default: quarkus.langchain4j.{provider}.embedding-model.model-name
            // - Named: quarkus.langchain4j.{provider}.{configName}.embedding-model.model-name
            String modelNameKey;
            if (NamedConfigUtil.isDefault(configName)) {
                modelNameKey = "quarkus.langchain4j." + provider + ".embedding-model.model-name";
            } else {
                modelNameKey = "quarkus.langchain4j." + provider + "." + configName + ".embedding-model.model-name";
            }
            String modelName = ConfigProvider.getConfig()
                    .getOptionalValue(modelNameKey, String.class)
                    .orElse(configName); // Fallback to configName if model-name not configured

            // Get EmbeddingModel as a CDI dependency using the creational context
            // This makes EmbeddingModel a proper dependency of EmbeddingService, so CDI ensures
            // it's available before creating this bean
            EmbeddingModel embeddingModel;
            if (NamedConfigUtil.isDefault(configName)) {
                // Default model - no qualifier
                embeddingModel = context.getInjectedReference(EmbeddingModel.class);
            } else {
                // Named model - use @ModelName qualifier
                embeddingModel = context.getInjectedReference(EmbeddingModel.class, ModelName.Literal.of(configName));
            }

            return new QuarkusEmbeddingService(modelName, provider, embeddingModel);
        };
    }
}