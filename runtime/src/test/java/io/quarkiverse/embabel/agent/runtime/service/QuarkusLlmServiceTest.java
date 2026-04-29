package io.quarkiverse.embabel.agent.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.prompt.PromptContributor;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Unit tests for {@link QuarkusLlmService}.
 */
class QuarkusLlmServiceTest {

    private ChatModel mockChatModel;
    private QuarkusLlmService service;

    @BeforeEach
    void setUp() {
        mockChatModel = mock(ChatModel.class);
        service = new QuarkusLlmService("gpt-4o", "openai", mockChatModel);
    }

    @Test
    void shouldReturnModelName() {
        assertThat(service.getName()).isEqualTo("gpt-4o");
    }

    @Test
    void shouldReturnProvider() {
        assertThat(service.getProvider()).isEqualTo("openai");
    }

    @Test
    void shouldReturnNullKnowledgeCutoffDateByDefault() {
        assertThat(service.getKnowledgeCutoffDate()).isNull();
    }

    @Test
    void shouldReturnNullPricingModelByDefault() {
        assertThat(service.getPricingModel()).isNull();
    }

    @Test
    void shouldReturnEmptyPromptContributorsByDefault() {
        assertThat(service.getPromptContributors()).isEmpty();
    }

    @Test
    void shouldReturnFalseForSupportsStreaming() {
        assertThat(service.supportsStreaming()).isFalse();
    }

    @Test
    void shouldReturnChatModel() {
        assertThat(service.getChatModel()).isSameAs(mockChatModel);
    }

    @Test
    void shouldThrowExceptionWhenCreatingMessageSender() {
        LlmOptions options = LlmOptions.withDefaults();
        assertThatThrownBy(() -> service.createMessageSender(options))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("QuarkusLlmMessageSender not yet implemented");
    }

    @Test
    void shouldThrowExceptionWhenCreatingMessageStreamer() {
        LlmOptions options = LlmOptions.withDefaults();
        assertThatThrownBy(() -> service.createMessageStreamer(options))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Streaming support not yet implemented");
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
    void shouldThrowExceptionWhenKnowledgeCutoffDateIsNull() {
        assertThatThrownBy(() -> service.withKnowledgeCutoffDate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Knowledge cutoff date cannot be null");
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
    void shouldThrowExceptionWhenPromptContributorIsNull() {
        assertThatThrownBy(() -> service.withPromptContributor(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Prompt contributor cannot be null");
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

    @Test
    void shouldReturnUnmodifiablePromptContributorsList() {
        PromptContributor contributor = PromptContributor.fixed("Test");
        QuarkusLlmService updated = service.withPromptContributor(contributor);

        assertThatThrownBy(() -> updated.getPromptContributors().add(PromptContributor.fixed("Another")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldThrowExceptionWhenModelNameIsNull() {
        assertThatThrownBy(() -> new QuarkusLlmService(null, "openai", mockChatModel))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Model name cannot be null");
    }

    @Test
    void shouldThrowExceptionWhenChatModelIsNull() {
        assertThatThrownBy(() -> new QuarkusLlmService("gpt-4o", "openai", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ChatModel cannot be null");
    }

    @Test
    void shouldAcceptDifferentProviders() {
        QuarkusLlmService anthropicService = new QuarkusLlmService("claude-3-5-sonnet", "anthropic", mockChatModel);
        assertThat(anthropicService.getProvider()).isEqualTo("anthropic");
        assertThat(anthropicService.getName()).isEqualTo("claude-3-5-sonnet");

        QuarkusLlmService ollamaService = new QuarkusLlmService("llama3.2", "ollama", mockChatModel);
        assertThat(ollamaService.getProvider()).isEqualTo("ollama");
        assertThat(ollamaService.getName()).isEqualTo("llama3.2");
    }
}