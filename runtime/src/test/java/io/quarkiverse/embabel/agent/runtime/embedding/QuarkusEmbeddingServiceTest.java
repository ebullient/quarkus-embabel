package io.quarkiverse.embabel.agent.runtime.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.embabel.common.ai.model.ModelType;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

/**
 * Unit tests for {@link QuarkusEmbeddingService}.
 * Tests the wrapper functionality without requiring actual embedding models.
 */
class QuarkusEmbeddingServiceTest {

    private EmbeddingModel mockModel;
    private QuarkusEmbeddingService service;

    @BeforeEach
    void setUp() {
        mockModel = mock(EmbeddingModel.class);
        service = new QuarkusEmbeddingService("test-model", "test-provider", mockModel);
    }

    @Test
    void testGetName() {
        assertEquals("test-model", service.getName());
    }

    @Test
    void testGetProvider() {
        assertEquals("test-provider", service.getProvider());
    }

    @Test
    void testGetType() {
        assertEquals(ModelType.EMBEDDING, service.getType());
    }

    @Test
    void testGetPricingModel() {
        // Default implementation returns null
        assertNull(service.getPricingModel());
    }

    @Test
    void testGetDimensions() {
        when(mockModel.dimension()).thenReturn(1536);

        assertEquals(1536, service.getDimensions());
        verify(mockModel).dimension();
    }

    @Test
    void testEmbedSingleText() {
        // Create mock embedding
        float[] vector = new float[] { 0.1f, 0.2f, 0.3f };
        Embedding mockEmbedding = new Embedding(vector);
        Response<Embedding> mockResponse = Response.from(mockEmbedding);

        when(mockModel.embed("test text")).thenReturn(mockResponse);

        // Test
        float[] result = service.embed("test text");

        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals(0.1f, result[0], 0.001f);
        assertEquals(0.2f, result[1], 0.001f);
        assertEquals(0.3f, result[2], 0.001f);

        verify(mockModel).embed("test text");
    }

    @Test
    void testEmbedMultipleTexts() {
        // Create mock embeddings
        Embedding embedding1 = new Embedding(new float[] { 0.1f, 0.2f });
        Embedding embedding2 = new Embedding(new float[] { 0.3f, 0.4f });
        Response<List<Embedding>> mockResponse = Response.from(Arrays.asList(embedding1, embedding2));

        when(mockModel.embedAll(anyList())).thenReturn(mockResponse);

        // Test
        List<String> texts = Arrays.asList("text1", "text2");
        List<float[]> results = service.embed(texts);

        assertNotNull(results);
        assertEquals(2, results.size());

        // Verify first embedding
        assertEquals(2, results.get(0).length);
        assertEquals(0.1f, results.get(0)[0], 0.001f);
        assertEquals(0.2f, results.get(0)[1], 0.001f);

        // Verify second embedding
        assertEquals(2, results.get(1).length);
        assertEquals(0.3f, results.get(1)[0], 0.001f);
        assertEquals(0.4f, results.get(1)[1], 0.001f);

        verify(mockModel).embedAll(argThat(segments -> {
            List<TextSegment> segmentList = (List<TextSegment>) segments;
            return segmentList.size() == 2 &&
                    segmentList.get(0).text().equals("text1") &&
                    segmentList.get(1).text().equals("text2");
        }));
    }

    @Test
    void testEmbedEmptyList() {
        Response<List<Embedding>> mockResponse = Response.from(Arrays.asList());
        when(mockModel.embedAll(anyList())).thenReturn(mockResponse);

        List<float[]> results = service.embed(Arrays.asList());

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testInfoString() {
        String info = service.infoString(false, 0);

        assertNotNull(info);
        assertTrue(info.contains("test-model"));
        assertTrue(info.contains("test-provider"));
    }

    @Test
    void testInfoStringWithIndent() {
        String info = service.infoString(false, 2);

        assertNotNull(info);
        assertTrue(info.startsWith("  ")); // Should be indented
    }
}