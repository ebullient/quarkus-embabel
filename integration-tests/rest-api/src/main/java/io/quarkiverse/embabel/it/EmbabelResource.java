package io.quarkiverse.embabel.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.embabel.agent.api.tool.config.ToolLoopConfiguration;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.common.ai.model.ConfigurableModelProviderProperties;
import com.embabel.common.ai.model.DefaultModelSelectionCriteria;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.ModelProvider;

/**
 * Simple REST endpoint to verify the Quarkus application starts successfully
 * with the Embabel Agent extension loaded.
 */
@Path("/embabel")
public class EmbabelResource {

    @Inject
    ModelProvider modelProvider;

    @Inject
    AgentPlatform agentPlatform;

    @Inject
    ToolLoopConfiguration toolLoopConfig;

    @Inject
    ConfigurableModelProviderProperties modelProviderProps;

    @ConfigProperty(name = "embabel.agent.platform.name")
    String platformName;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Embabel Agent extension is loaded";
    }

    @GET
    @Path("/models")
    @Produces(MediaType.TEXT_PLAIN)
    public String models() {
        return modelProvider.infoString(true, 0);
    }

    @GET
    @Path("/embeddings/default")
    @Produces(MediaType.TEXT_PLAIN)
    public String defaultEmbedding() {
        EmbeddingService embeddingService = modelProvider.getEmbeddingService(DefaultModelSelectionCriteria.INSTANCE);
        return embeddingService.getProvider() + ":" + embeddingService.getName();
    }

    /**
     * Returns configuration details to verify that Embabel configurations
     * are properly bound and injected.
     */
    @GET
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    public ConfigInfo config() {
        return new ConfigInfo(
                platformName,
                agentPlatform.getName(),
                agentPlatform.getDescription(),
                toolLoopConfig.getType().name(),
                toolLoopConfig.getMaxIterations(),
                modelProviderProps.getDefaultLlm(),
                modelProviderProps.getDefaultEmbeddingModel());
    }

    /**
     * Configuration information DTO for testing config binding.
     */
    public record ConfigInfo(
            String platformNameFromConfig,
            String platformNameFromBean,
            String platformDescription,
            String toolLoopType,
            int toolLoopMaxIterations,
            String defaultLlm,
            String defaultEmbeddingModel) {
    }
}
