package io.quarkiverse.embabel.agent.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.embabel.chat.UserMessage;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.embabel.agent.runtime.message.MessageConverterImpl;

/**
 * Unit tests for {@link QuarkusStreamingLlmService}.
 */
class QuarkusStreamingLlmServiceTest {

    @Test
    void shouldStreamTextResponse() {
        RecordingStreamingChatModel streamingChatModel = new RecordingStreamingChatModel(List.of("Hel", "lo"), null);
        QuarkusStreamingLlmService service = new QuarkusStreamingLlmService(streamingChatModel, new MessageConverterImpl());

        List<String> tokens = service.streamResponse(List.of(new UserMessage("Hello")))
                .collectList()
                .block();

        assertThat(tokens).containsExactly("Hel", "lo");
        assertThat(streamingChatModel.recordedRequest()).isNotNull();
        assertThat(streamingChatModel.recordedRequest().messages()).hasSize(1);
        assertThat(streamingChatModel.recordedRequest().messages().get(0)).isInstanceOf(ChatMessage.class);
    }

    @Test
    void shouldCompleteWhenStreamingFinishes() {
        RecordingStreamingChatModel streamingChatModel = new RecordingStreamingChatModel(List.of("Done"), null);
        QuarkusStreamingLlmService service = new QuarkusStreamingLlmService(streamingChatModel, new MessageConverterImpl());

        List<String> tokens = service.streamResponse(List.of(new UserMessage("Complete")))
                .collectList()
                .block();

        assertThat(tokens).containsExactly("Done");
    }

    @Test
    void shouldPropagateStreamingErrors() {
        RuntimeException error = new RuntimeException("stream failed");
        RecordingStreamingChatModel streamingChatModel = new RecordingStreamingChatModel(List.of(), error);
        QuarkusStreamingLlmService service = new QuarkusStreamingLlmService(streamingChatModel, new MessageConverterImpl());

        Throwable thrown = null;
        try {
            service.streamResponse(List.of(new UserMessage("Fail")))
                    .collectList()
                    .block();
        } catch (Throwable t) {
            thrown = t;
        }

        assertThat(thrown).isNotNull();
        assertThat(thrown).hasMessageContaining("stream failed");
    }

    private static final class RecordingStreamingChatModel implements StreamingChatModel {

        private final List<String> partialResponses;
        private final RuntimeException error;
        private ChatRequest recordedRequest;

        private RecordingStreamingChatModel(List<String> partialResponses, RuntimeException error) {
            this.partialResponses = new ArrayList<>(partialResponses);
            this.error = error;
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            this.recordedRequest = chatRequest;

            if (error != null) {
                handler.onError(error);
                return;
            }

            for (String partialResponse : partialResponses) {
                handler.onPartialResponse(partialResponse);
            }

            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from(String.join("", partialResponses)))
                    .build());
        }

        ChatRequest recordedRequest() {
            return recordedRequest;
        }
    }
}