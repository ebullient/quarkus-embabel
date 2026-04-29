package io.quarkiverse.embabel.agent.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.embabel.agent.spi.LlmService;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.embabel.agent.runtime.service.QuarkusLlmService;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Deployment test to verify that multiple LlmService beans are properly registered
 * when multiple ChatModels are configured (default + named models).
 */
class MultipleModelsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            // Default model
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.model-name", "gpt-4o")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost:8080/mock")
            // Named "fast" model
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.fast.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.fast.chat-model.model-name", "gpt-4o-mini")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.fast.base-url", "http://localhost:8080/mock");

    @Inject
    ChatModel defaultChatModel;

    @Inject
    @ModelName("fast")
    ChatModel fastChatModel;

    @Inject
    QuarkusLlmService defaultLlmService;

    @Inject
    @ModelName("fast")
    QuarkusLlmService fastLlmService;

    @Inject
    LlmService<?> defaultLlmServiceViaInterface;

    @Inject
    @ModelName("fast")
    LlmService<?> fastLlmServiceViaInterface;

    @Test
    void testBothChatModelsAreInjected() {
        assertThat(defaultChatModel)
                .isNotNull()
                .as("Default ChatModel should be injected");
        assertThat(fastChatModel)
                .isNotNull()
                .as("Fast ChatModel should be injected");
    }

    @Test
    void testBothLlmServicesAreInjected() {
        assertThat(defaultLlmService)
                .isNotNull()
                .as("Default LlmService should be injected");
        assertThat(fastLlmService)
                .isNotNull()
                .as("Fast LlmService should be injected");
    }

    @Test
    void testLlmServicesAreDifferentInstances() {
        assertThat(ClientProxy.unwrap(defaultLlmService))
                .isNotSameAs(ClientProxy.unwrap(fastLlmService))
                .as("Default and fast LlmServices should be different instances");
    }

    @Test
    void testDefaultLlmServiceWrapsDefaultChatModel() {
        QuarkusLlmService unwrappedService = (QuarkusLlmService) ClientProxy.unwrap(defaultLlmService);
        ChatModel unwrappedFromService = ClientProxy.unwrap(unwrappedService.getChatModel());
        ChatModel unwrappedInjected = ClientProxy.unwrap(defaultChatModel);

        assertThat(unwrappedFromService)
                .isSameAs(unwrappedInjected)
                .as("Default LlmService should wrap the default ChatModel");
    }

    @Test
    void testFastLlmServiceWrapsFastChatModel() {
        QuarkusLlmService unwrappedService = (QuarkusLlmService) ClientProxy.unwrap(fastLlmService);
        ChatModel unwrappedFromService = ClientProxy.unwrap(unwrappedService.getChatModel());
        ChatModel unwrappedInjected = ClientProxy.unwrap(fastChatModel);

        assertThat(unwrappedFromService)
                .isSameAs(unwrappedInjected)
                .as("Fast LlmService should wrap the fast ChatModel");
    }

    @Test
    void testDefaultLlmServiceHasCorrectProvider() {
        assertThat(defaultLlmService.getProvider())
                .isEqualTo("openai")
                .as("Default LlmService should have openai provider");
    }

    @Test
    void testFastLlmServiceHasCorrectProvider() {
        assertThat(fastLlmService.getProvider())
                .isEqualTo("openai")
                .as("Fast LlmService should have openai provider");
    }

    @Test
    void testDefaultLlmServiceInterfaceInjection() {
        assertThat(defaultLlmServiceViaInterface)
                .isNotNull()
                .as("Default LlmService should be injectable via interface");

        assertThat(ClientProxy.unwrap(defaultLlmServiceViaInterface))
                .isInstanceOf(QuarkusLlmService.class)
                .isSameAs(ClientProxy.unwrap(defaultLlmService))
                .as("Interface injection should return same bean as concrete injection");
    }

    @Test
    void testFastLlmServiceInterfaceInjection() {
        assertThat(fastLlmServiceViaInterface)
                .isNotNull()
                .as("Fast LlmService should be injectable via interface");

        assertThat(ClientProxy.unwrap(fastLlmServiceViaInterface))
                .isInstanceOf(QuarkusLlmService.class)
                .isSameAs(ClientProxy.unwrap(fastLlmService))
                .as("Interface injection should return same bean as concrete injection");
    }
}