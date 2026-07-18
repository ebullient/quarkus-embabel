package io.quarkiverse.embabel.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.spi.LlmService;
import com.embabel.agent.spi.loop.streaming.LlmMessageStreamer;
import com.embabel.chat.UserMessage;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.model.ModelSelectionCriteria;

import io.quarkus.test.junit.QuarkusTest;
import reactor.core.publisher.Flux;

@QuarkusTest
class StreamingIntegrationTest {

    @Inject
    AgentPlatform agentPlatform;

    @Test
    void shouldSupportStreaming() {
        LlmService<?> llmService = agentPlatform.getPlatformServices()
                .modelProvider()
                .getLlm(ModelSelectionCriteria.Companion.getPlatformDefault());

        assertThat(llmService.supportsStreaming()).isTrue();
    }

    @Test
    void shouldStreamResponse() {
        LlmService<?> llmService = agentPlatform.getPlatformServices()
                .modelProvider()
                .getLlm(ModelSelectionCriteria.Companion.getPlatformDefault());

        LlmMessageStreamer streamer = llmService.createMessageStreamer(new LlmOptions());
        Flux<String> flux = streamer.stream(
                List.of(new UserMessage("Say hello")),
                Collections.emptyList(),
                Collections.emptyList());

        List<String> chunks = flux.collectList().block();
        assertThat(chunks).isNotEmpty();
        String combined = String.join("", chunks);
        assertThat(combined).isNotBlank();
    }
}
