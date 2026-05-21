package io.quarkiverse.embabel.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import com.embabel.common.ai.model.ByNameModelSelectionCriteria;
import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.ModelProvider;

/**
 * REST resource for testing embedding functionality.
 * <p>
 * This resource provides endpoints to test the {@link EmbeddingService} integration
 * with LangChain4j embedding models via the Quarkus Embabel extension.
 */
@Path("/embedding")
public class EmbeddingResource {

    @Inject
    ModelProvider modelProvider;

    /**
     * Generates an embedding for the provided text.
     *
     * @param text the text to embed
     * @return the embedding response containing the text, embedding vector, and dimensions
     */
    @GET
    @Path("/embed")
    @Produces(MediaType.APPLICATION_JSON)
    public EmbeddingResponse embed(@QueryParam("text") String text) {
        // Handle empty or null text - use a placeholder as LangChain4j requires non-blank text
        String textToEmbed = (text == null || text.trim().isEmpty()) ? "empty" : text;

        EmbeddingService embeddingService = modelProvider.getEmbeddingService(
                new ByNameModelSelectionCriteria("text-embedding-ada-002"));
        float[] embedding = embeddingService.embed(textToEmbed);
        return new EmbeddingResponse(text, embedding, embedding.length);
    }

    /**
     * Response object containing embedding results.
     */
    public static class EmbeddingResponse {
        public String text;
        public float[] embedding;
        public int dimensions;

        public EmbeddingResponse() {
        }

        public EmbeddingResponse(String text, float[] embedding, int dimensions) {
            this.text = text;
            this.embedding = embedding;
            this.dimensions = dimensions;
        }
    }
}