package io.quarkiverse.embabel.it.agent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.mockito.Mockito;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.Mock;

/**
 * Produces a mock ChatModel bean for testing.
 * This allows @InjectMock to work properly in integration tests.
 */
@Mock
@ApplicationScoped
public class MockChatModelProducer {

    @Produces
    @ApplicationScoped
    public ChatModel chatModel() {
        return Mockito.mock(ChatModel.class);
    }
}