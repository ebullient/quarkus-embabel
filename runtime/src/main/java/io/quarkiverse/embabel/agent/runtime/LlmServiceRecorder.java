package io.quarkiverse.embabel.agent.runtime;

import java.util.function.Function;

import jakarta.enterprise.inject.Instance;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.embabel.agent.runtime.service.QuarkusLlmService;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.Arc;
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
     * The function looks up the ChatModel bean by qualifier (for named models)
     * or without qualifier (for the default model), then creates a QuarkusLlmService
     * wrapping that ChatModel.
     *
     * @param configName the configuration name (e.g., "fast", "claude", or default)
     * @param provider the provider name (e.g., "openai", "anthropic", "ollama")
     * @return a function that creates the QuarkusLlmService instance
     */
    public Function<SyntheticCreationalContext<QuarkusLlmService>, QuarkusLlmService> createLlmService(String configName,
            String provider) {

        return context -> {
            // Lookup ChatModel by qualifier at runtime
            Instance<ChatModel> chatModels = Arc.container().select(ChatModel.class);

            ChatModel chatModel;
            if (NamedConfigUtil.isDefault(configName)) {
                // Default model has no qualifier
                chatModel = chatModels.get();
            } else {
                // Named model uses @ModelName qualifier
                chatModel = chatModels.select(ModelName.Literal.of(configName)).get();
            }

            // Get model name from configuration
            // TODO: Extract actual model name from ChatModel configuration
            // For now, use configName as a placeholder
            String modelName = configName;

            return new QuarkusLlmService(modelName, provider, chatModel);
        };
    }
}