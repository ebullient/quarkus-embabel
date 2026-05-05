package io.quarkiverse.embabel.agent.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.embabel.agent.spi.loop.LlmMessageSender;
import com.embabel.agent.spi.loop.streaming.LlmMessageStreamer;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.prompt.PromptContributor;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * Unit tests for {@link QuarkusLlmService}.
 */
class QuarkusLlmServiceTest {

    private ChatModel mockChatModel;
    private StreamingChatModel mockStreamingChatModel;
    private QuarkusLlmService service;

    @BeforeEach
    void setUp() {
        mockChatModel = mock(ChatModel.class);
        mockStreamingChatModel = mock(StreamingChatModel.class);
        service = new QuarkusLlmService("gpt-4o", "openai", mockChatModel);
    }

    @Test
    void shouldReturnFalseForSupportsStreamingWithoutStreamingModel() {
        assertThat(service.supportsStreaming()).isFalse();
    }

    @Test
    void shouldReturnTrueForSupportsStreamingWithStreamingModel() {
        QuarkusLlmService streamingService = new QuarkusLlmService("gpt-4o", "openai", mockChatModel, mockStreamingChatModel);
        assertThat(streamingService.supportsStreaming()).isTrue();
    }

    @Test
    void shouldCreateMessageSender() {
        LlmOptions options = LlmOptions.withDefaults();
        LlmMessageSender sender = service.createMessageSender(options);

        assertThat(sender).isNotNull();
        assertThat(sender).isInstanceOf(QuarkusLlmMessageSender.class);
    }

    @Test
    void shouldThrowExceptionWhenCreatingMessageStreamerWithoutStreamingModel() {
        LlmOptions options = LlmOptions.withDefaults();
        assertThatThrownBy(() -> service.createMessageStreamer(options))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Streaming support is not available");
    }

    @Test
    void shouldCreateMessageStreamerWhenStreamingModelIsAvailable() {
        QuarkusLlmService streamingService = new QuarkusLlmService("gpt-4o", "openai", mockChatModel, mockStreamingChatModel);
        LlmMessageStreamer streamer = streamingService.createMessageStreamer(LlmOptions.withDefaults());

        assertThat(streamer).isNotNull();
    }

    @Test
    void shouldCreateNewInstanceWithKnowledgeCutoffDate() {
        LocalDate cutoffDate = LocalDate.of(2024, 1, 1);
        QuarkusLlmService updated = service.withKnowledgeCutoffDate(cutoffDate);

        assertThat(updated).isNotSameAs(service);
        assertThat(updated.getKnowledgeCutoffDate()).isEqualTo(cutoffDate);
        assertThat(updated.getName()).isEqualTo(service.getName());
        assertThat(updated.getProvider()).isEqualTo(service.getProvider());
        assertThat(updated.getChatModel()).isSameAs(service.getChatModel());
        assertThat(updated.getPromptContributors()).hasSize(1);
    }

    @Test
    void shouldCreateNewInstanceWithPromptContributor() {
        PromptContributor contributor = PromptContributor.fixed("Test prompt");
        QuarkusLlmService updated = service.withPromptContributor(contributor);

        assertThat(updated).isNotSameAs(service);
        assertThat(updated.getPromptContributors()).hasSize(1);
        assertThat(updated.getPromptContributors().get(0)).isSameAs(contributor);
        assertThat(updated.getName()).isEqualTo(service.getName());
        assertThat(updated.getProvider()).isEqualTo(service.getProvider());
        assertThat(updated.getChatModel()).isSameAs(service.getChatModel());
    }

    @Test
    void shouldAccumulatePromptContributors() {
        PromptContributor contributor1 = PromptContributor.fixed("First");
        PromptContributor contributor2 = PromptContributor.fixed("Second");

        QuarkusLlmService updated = service
                .withPromptContributor(contributor1)
                .withPromptContributor(contributor2);

        assertThat(updated.getPromptContributors()).hasSize(2);
        assertThat(updated.getPromptContributors().get(0)).isSameAs(contributor1);
        assertThat(updated.getPromptContributors().get(1)).isSameAs(contributor2);
    }

    @Test
    void shouldPreserveExistingContributorsWhenAddingKnowledgeCutoffDate() {
        PromptContributor contributor = PromptContributor.fixed("Test");
        LocalDate cutoffDate = LocalDate.of(2024, 1, 1);

        QuarkusLlmService updated = service
                .withPromptContributor(contributor)
                .withKnowledgeCutoffDate(cutoffDate);

        assertThat(updated.getPromptContributors()).hasSize(2);
        assertThat(updated.getKnowledgeCutoffDate()).isEqualTo(cutoffDate);
    }

}