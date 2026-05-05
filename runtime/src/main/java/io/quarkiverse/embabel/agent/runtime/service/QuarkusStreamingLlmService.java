package io.quarkiverse.embabel.agent.runtime.service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.embabel.chat.Message;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.embabel.agent.runtime.message.MessageConverter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * Adapter that bridges LangChain4j {@link StreamingChatModel} callbacks to Reactor {@link Flux}.
 * <p>
 * This service converts Embabel messages to LangChain4j messages, invokes the configured
 * streaming chat model, and emits partial text responses through a Flux.
 */
public class QuarkusStreamingLlmService {

    private final StreamingChatModel streamingChatModel;
    private final MessageConverter messageConverter;

    /**
     * Create a new streaming service adapter.
     *
     * @param streamingChatModel the LangChain4j streaming chat model
     * @param messageConverter the Embabel/LangChain4j message converter
     */
    public QuarkusStreamingLlmService(StreamingChatModel streamingChatModel, MessageConverter messageConverter) {
        this.streamingChatModel = Objects.requireNonNull(streamingChatModel, "StreamingChatModel cannot be null");
        this.messageConverter = Objects.requireNonNull(messageConverter, "MessageConverter cannot be null");
    }

    /**
     * Stream partial text responses for the given messages.
     *
     * @param messages the Embabel conversation messages
     * @return a Flux emitting partial response text as it is received
     */
    public Flux<String> streamResponse(List<? extends Message> messages) {
        Objects.requireNonNull(messages, "Messages cannot be null");

        List<ChatMessage> lc4jMessages = messages.stream()
                .map(messageConverter::toLangChain4j)
                .collect(Collectors.toList());

        ChatRequest request = ChatRequest.builder()
                .messages(lc4jMessages)
                .build();

        return Flux.create(emitter -> streamingChatModel.chat(request, new StreamingFluxResponseHandler(emitter)),
                FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * Streaming response handler that forwards LangChain4j callbacks to a Flux emitter.
     */
    static final class StreamingFluxResponseHandler implements StreamingChatResponseHandler {

        private final FluxSink<String> emitter;

        StreamingFluxResponseHandler(FluxSink<String> emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onPartialResponse(String partialResponse) {
            if (!emitter.isCancelled()) {
                emitter.next(partialResponse);
            }
        }

        @Override
        public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
            if (!emitter.isCancelled()) {
                emitter.complete();
            }
        }

        @Override
        public void onError(Throwable error) {
            if (!emitter.isCancelled()) {
                emitter.error(error);
            }
        }
    }
}