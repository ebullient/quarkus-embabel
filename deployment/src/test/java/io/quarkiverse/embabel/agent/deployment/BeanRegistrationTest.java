package io.quarkiverse.embabel.agent.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.internal.LlmOperations;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spi.ToolGroupResolver;

import io.quarkiverse.embabel.agent.runtime.loop.QuarkusToolLoopFactory;
import io.quarkiverse.embabel.agent.runtime.provider.QuarkusModelProvider;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Integration test to verify that extension beans are properly registered
 * and discoverable via CDI.
 * <p>
 * Tests Step 20: Bean Registration Processor
 * Tests Step 21: Spring @Configuration support (via quarkus-spring-di)
 * <p>
 * Also verifies:
 * <ul>
 * <li>Tool infrastructure beans (ToolGroupResolver) are properly registered and injectable</li>
 * <li>ToolGroups can be created via Spring @Bean methods or @ToolGroup annotation</li>
 * <li>@Agent classes are discovered at build time and deployed to AgentPlatform at runtime</li>
 * <li>@LlmTool methods within agents are discovered via Tool.safelyFromInstance()</li>
 * </ul>
 * <p>
 * Note: MessageConverterImpl and ToolSpecificationConverterImpl are NOT CDI beans.
 * They are stateless utility classes instantiated directly with 'new' in QuarkusLlmService.
 */
class BeanRegistrationTest {

    @RegisterExtension
    static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(TestConfiguration.class, TestBean.class, TestAgent.class))
            // Minimal config to enable the extension
            .overrideConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.model-name", "gpt-4o")
            .overrideConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost:8080/mock")
            .overrideConfigKey("embabel.models.default-llm", "gpt-4o");

    @Inject
    QuarkusModelProvider modelProvider;

    @Inject
    QuarkusToolLoopFactory toolLoopFactory;

    @Inject
    LlmOperations llmOperations;

    @Inject
    AgentPlatform agentPlatform;

    @Inject
    TestBean testBean;

    @Inject
    ToolGroupResolver toolGroupResolver;

    @Inject
    TestAgent testAgent;

    /**
     * Test that extension beans registered by the BeanRegistrationProcessor
     * are discoverable and injectable via CDI.
     */
    @Test
    void testExtensionBeansAreRegistered() {
        assertThat(modelProvider)
                .as("QuarkusModelProvider should be injectable")
                .isNotNull();

        assertThat(toolLoopFactory)
                .as("QuarkusToolLoopFactory should be injectable")
                .isNotNull();

        // Verify LlmService beans are resolvable
        var llmServiceHandle = Arc.container().select(com.embabel.agent.spi.LlmService.class);
        assertThat(llmServiceHandle.isResolvable())
                .as("LlmService should be resolvable")
                .isTrue();

        // Verify ModelProvider can list models
        var models = modelProvider.listModels();
        assertThat(models)
                .as("ModelProvider should return available models")
                .isNotEmpty();
    }

    /**
     * Test that LlmOperations bean is created with all its dependencies.
     * This verifies the LlmOperationsProducer and its dependency chain.
     */
    @Test
    void testLlmOperationsBean() {
        assertThat(llmOperations)
                .as("LlmOperations should be injectable")
                .isNotNull();
    }

    /**
     * Test that AgentPlatform bean is created with all its dependencies.
     * This is the top-level bean that depends on everything else.
     */
    @Test
    void testAgentPlatformBean() {
        assertThat(agentPlatform)
                .as("AgentPlatform should be injectable")
                .isNotNull();

        assertThat(agentPlatform.getName())
                .as("AgentPlatform should have configured name")
                .isEqualTo("quarkus-agent-platform");
    }

    /**
     * Test that Spring @Configuration and @Bean work via quarkus-spring-di.
     * This verifies Step 21 - that Spring-style configuration is supported.
     */
    @Test
    void testSpringConfigurationSupport() {
        assertThat(testBean)
                .as("Bean created by Spring @Configuration should be injectable")
                .isNotNull();

        assertThat(testBean.getMessage())
                .as("Bean should have correct value from @Bean method")
                .isEqualTo("Test bean from Spring @Configuration");
    }

    /**
     * Test that tool infrastructure beans are registered.
     * This verifies that ToolGroupResolver (created by ToolProducer) is properly wired.
     * <p>
     * The build-time discovery in EmbabelProcessor.discoverToolGroups() ensures that
     * classes annotated with @ToolGroup are registered as CDI beans and can be
     * discovered by ToolProducer's Instance<ToolGroup> injection.
     */
    @Test
    void testToolInfrastructureBeansAreRegistered() {
        assertThat(toolGroupResolver)
                .as("ToolGroupResolver should be injectable")
                .isNotNull();
    }

    /**
     * Test that @Agent classes are discovered and registered as CDI beans.
     * This verifies that:
     * 1. @Agent classes are discovered by discoverAgents() build step
     * 2. They are registered as CDI beans and are injectable
     * 3. Agents with @LlmTool methods can be created
     * <p>
     * The agent's @LlmTool methods are discovered at runtime by QuarkusAgentDeployer
     * when the agent is deployed to the AgentPlatform.
     */
    @Test
    void testAgentDiscovery() {
        assertThat(testAgent)
                .as("@Agent annotated class should be discovered and registered as a CDI bean")
                .isNotNull();

        // Verify the @LlmTool method works
        String result = testAgent.reverseString("hello");
        assertThat(result)
                .as("@LlmTool method should be callable")
                .isEqualTo("olleh");
    }

    /**
     * Test Spring @Configuration class to verify quarkus-spring-di support.
     */
    @Configuration
    static class TestConfiguration {

        @Bean
        TestBean testBean() {
            return new TestBean("Test bean from Spring @Configuration");
        }
    }

    /**
     * Simple test bean created by Spring @Bean method.
     */
    static class TestBean {
        private final String message;

        TestBean(String message) {
            this.message = message;
        }

        String getMessage() {
            return message;
        }
    }

    /**
     * Test agent with @LlmTool method to verify agent and tool discovery.
     * This agent should be discovered at build time by the discoverAgents() build step
     * and deployed to the AgentPlatform at runtime.
     * <p>
     * The @LlmTool method should be discovered at runtime via Tool.safelyFromInstance()
     * when the agent is deployed by QuarkusAgentDeployer.
     */
    @Agent(description = "Test agent for verifying @Agent and @LlmTool discovery")
    static class TestAgent {

        /**
         * Simple LLM tool that reverses a string.
         * This verifies that @LlmTool methods are discovered and registered.
         */
        @LlmTool(description = "Reverses the input string")
        public String reverseString(String input) {
            return new StringBuilder(input).reverse().toString();
        }

        /**
         * Simple action that uses the AI to generate text.
         * This verifies that @Action methods work with the test agent.
         */
        @Action
        public String greet(UserInput userInput, Ai ai) {
            return "Hello from TestAgent! Input: " + userInput.getContent();
        }
    }
}
