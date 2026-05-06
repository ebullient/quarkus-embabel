package io.quarkiverse.embabel.agent.runtime.embedding;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import com.embabel.common.ai.model.EmbeddingService;
import com.embabel.common.ai.model.ModelType;
import com.embabel.common.ai.model.PricingModel;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

/**
 * Quarkus implementation of {@link EmbeddingService} that wraps a LangChain4j {@link EmbeddingModel}.
 * <p>
 * This service provides embedding generation capabilities using LangChain4j's embedding models,
 * which are automatically configured and injected by the quarkus-langchain4j extension.
 * <p>
 * <b>Architecture</b>:
 * <ul>
 * <li>Wraps a LangChain4j {@link EmbeddingModel} (e.g., OpenAI, Ollama)</li>
 * <li>Converts between Embabel's float[] format and LangChain4j's {@link Embedding} format</li>
 * <li>Supports both single and batch embedding operations</li>
 * </ul>
 * <p>
 * <b>Configuration Example</b>:
 *
 * <pre>
 * # OpenAI embeddings
 * quarkus.langchain4j.openai.embedding-model.model-name=text-embedding-3-small
 * quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
 *
 * # Ollama embeddings
 * quarkus.langchain4j.ollama.embedding-model.model-id=nomic-embed-text
 * quarkus.langchain4j.ollama.base-url=http://localhost:11434
 * </pre>
 *
 * @see EmbeddingService
 * @see EmbeddingModel
 */
@ApplicationScoped
public class QuarkusEmbeddingService implements EmbeddingService {

    private final String name;
    private final String provider;
    private final PricingModel pricingModel;
    private final EmbeddingModel embeddingModel;

    /**
     * Creates a new embedding service.
     *
     * @param name the model name (e.g., "text-embedding-3-small", "nomic-embed-text")
     * @param provider the provider name (e.g., "openai", "ollama")
     * @param embeddingModel the LangChain4j embedding model
     */
    public QuarkusEmbeddingService(String name, String provider, EmbeddingModel embeddingModel) {
        this(name, provider, embeddingModel, null);
    }

    /**
     * Creates a new embedding service with pricing information.
     *
     * @param name the model name (e.g., "text-embedding-3-small", "nomic-embed-text")
     * @param provider the provider name (e.g., "openai", "ollama")
     * @param embeddingModel the LangChain4j embedding model
     * @param pricingModel the pricing model (null for local/free models like Ollama)
     */
    public QuarkusEmbeddingService(String name, String provider, EmbeddingModel embeddingModel, PricingModel pricingModel) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.provider = Objects.requireNonNull(provider, "provider cannot be null");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel cannot be null");
        this.pricingModel = pricingModel; // null is valid for local models
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getProvider() {
        return provider;
    }

    @Override
    public PricingModel getPricingModel() {
        return pricingModel;
    }

    @Override
    public ModelType getType() {
        return ModelType.EMBEDDING;
    }

    @Override
    public int getDimensions() {
        return embeddingModel.dimension();
    }

    @Override
    public float[] embed(String text) {
        Objects.requireNonNull(text, "text cannot be null");
        Response<Embedding> response = embeddingModel.embed(text);
        return toFloatArray(response.content().vectorAsList());
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        Objects.requireNonNull(texts, "texts cannot be null");
        List<TextSegment> segments = texts.stream()
                .map(TextSegment::from)
                .collect(Collectors.toList());
        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        return response.content().stream()
                .map(e -> toFloatArray(e.vectorAsList()))
                .collect(Collectors.toList());
    }

    /**
     * Converts a List of Float objects to a primitive float array.
     *
     * @param list the list of Float objects
     * @return the primitive float array
     */
    private float[] toFloatArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    @Override
    public String toString() {
        return "QuarkusEmbeddingService{" +
                "name='" + name + '\'' +
                ", provider='" + provider + '\'' +
                ", dimensions=" + getDimensions() +
                ", pricingModel=" + (pricingModel != null ? "configured" : "null") +
                '}';
    }
}