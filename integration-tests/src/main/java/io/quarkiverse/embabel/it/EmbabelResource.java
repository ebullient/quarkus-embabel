package io.quarkiverse.embabel.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
}
