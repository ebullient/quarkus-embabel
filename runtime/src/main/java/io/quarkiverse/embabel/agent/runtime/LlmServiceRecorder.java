package io.quarkiverse.embabel.agent.runtime;

import java.util.function.Function;

import org.eclipse.microprofile.config.ConfigProvider;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.embabel.agent.runtime.service.QuarkusLlmService;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Runtime recorder for creating LlmService instances.
 * <p>
 * This recorder is invoked at runtime initialization to create {@link QuarkusLlmService}
 * instances for each configured ChatModel. It looks up the appropriate ChatModel bean
 * using CDI qualifiers and wraps it in a QuarkusLlmService.
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

            // Get ChatModel as a CDI dependency using the creational context
            // This makes ChatModel a proper dependency of LlmService, so CDI ensures
            // it's available before creating this bean
            ChatModel chatModel;
            if (NamedConfigUtil.isDefault(configName)) {
                // Default model - no qualifier
                chatModel = context.getInjectedReference(ChatModel.class);
            } else {
                // Named model - use @ModelName qualifier
                chatModel = context.getInjectedReference(ChatModel.class, ModelName.Literal.of(configName));
            }

            return new QuarkusLlmService(modelName, provider, chatModel);
        };
    }
}