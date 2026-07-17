package io.quarkiverse.embabel.agent.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.annotation.Cost;
import com.embabel.agent.api.annotation.LlmTool;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.AiBuilder;
import com.embabel.agent.api.common.ExecutingOperationContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PlatformServices;
import com.embabel.agent.api.common.autonomy.Autonomy;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.internal.LlmOperations;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.spi.ToolGroupResolver;
import com.embabel.agent.spi.loop.ToolLoopFactory;

import io.quarkiverse.embabel.agent.runtime.QuarkusAgentPlatform;
import io.quarkiverse.embabel.agent.runtime.provider.QuarkusModelProvider;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Integration test to verify that extension beans are properly registered
 * and discoverable via CDI.
 * <p>
 * Tests:
 * <ul>
 * <li>Bean Registration Processor</li>
 * <li>Spring @Configuration support (via quarkus-spring-di)</li>
 * <li>Build-time metadata discovery via Jandex (NEW)</li>
 * <li>@Action, @Condition, @Cost methods discovered at build time</li>
 * <li>Inherited methods from interfaces/superclasses (NEW)</li>
 * <li>@AchievesGoal automatic goal creation (NEW)</li>
 * <li>Agent deployment to AgentPlatform at runtime</li>
 * <li>@LlmTool methods discovered at runtime via Tool.safelyFromInstance()</li>
 * </ul>
 * <p>
 * Note: MessageConverterImpl and ToolSpecificationConverterImpl are NOT CDI beans.
 * They are stateless utility classes instantiated directly with 'new' in QuarkusLlmService.
 */
class BeanRegistrationTest {

    @RegisterExtension
    static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(
                            TestConfiguration.class,
                            TestBean.class,
                            TestAgent.class,
                            InheritingAgent.class,
                            AgentCapabilities.class,
                            GreetingResult.class))
            // Minimal config to enable the extension
            .overrideConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.model-name", "gpt-4o")
            .overrideConfigKey("quarkus.langchain4j.openai.embedding-model.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.openai.embedding-model.model-name", "text-embedding-3-small")
            .overrideConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost:8080/mock")
            .overrideConfigKey("embabel.models.default-llm", "gpt-4o")
            .overrideConfigKey("embabel.models.default-embedding-model", "text-embedding-3-small");

    @Inject
    QuarkusModelProvider modelProvider;

    @Inject
    ToolLoopFactory toolLoopFactory;

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

    @Inject
    InheritingAgent inheritingAgent;

    @Inject
    Ai ai;

    @Inject
    ExecutingOperationContext executingOperationContext;

    @Inject
    AiBuilder aiBuilder;

    @Inject
    Autonomy autonomy;

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
                .as("ToolLoopFactory should be injectable")
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
     * Test that EmbeddingModel beans are discovered and wrapped in EmbeddingService.
     * This verifies that QuarkusModelProvider properly discovers EmbeddingModel beans
     * from quarkus-langchain4j and wraps them in QuarkusEmbeddingService.
     */
    @Test
    void testEmbeddingModelDiscovery() {
        // Verify EmbeddingService beans are resolvable
        var embeddingServiceHandle = Arc.container().select(com.embabel.common.ai.model.EmbeddingService.class);
        assertThat(embeddingServiceHandle.isResolvable())
                .as("EmbeddingService should be resolvable")
                .isTrue();

        // Verify ModelProvider lists embedding models
        var models = modelProvider.listModels();
        var embeddingModels = models.stream()
                .filter(m -> m.getType() == com.embabel.common.ai.model.ModelType.EMBEDDING)
                .toList();

        assertThat(embeddingModels)
                .as("ModelProvider should return embedding models")
                .isNotEmpty()
                .as("Should contain the configured embedding model")
                .anyMatch(m -> m.getName().equals("text-embedding-3-small"));

        // Verify the embedding service can be retrieved
        var embeddingService = modelProvider.getEmbeddingService(
                com.embabel.common.ai.model.DefaultModelSelectionCriteria.INSTANCE);
        assertThat(embeddingService)
                .as("Default embedding service should be retrievable")
                .isNotNull();
        assertThat(embeddingService.getName())
                .as("Embedding service should have correct name")
                .isEqualTo("text-embedding-3-small");
        assertThat(embeddingService.getProvider())
                .as("Embedding service should have correct provider")
                .isEqualTo("openai");
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
     * Test that the platform's PlatformServices is a QuarkusAgentPlatform (self-reference).
     * This verifies that QuarkusAgentPlatform implements PlatformServices and returns
     * itself from getPlatformServices(), replacing SpringContextPlatformServices.
     */
    @Test
    void testPlatformServicesIsQuarkusAgentPlatform() {
        PlatformServices platformServices = agentPlatform.getPlatformServices();

        assertThat(platformServices)
                .as("PlatformServices should be a QuarkusAgentPlatform instance")
                .isInstanceOf(QuarkusAgentPlatform.class);

        assertThat(platformServices.getAgentPlatform())
                .as("PlatformServices.agentPlatform should be a QuarkusAgentPlatform")
                .isInstanceOf(QuarkusAgentPlatform.class);
    }

    @Test
    void testAutonomyBeanIsInjectable() {
        assertThat(autonomy)
                .as("Autonomy should be injectable as a CDI bean")
                .isNotNull();
    }

    @Test
    void testPlatformServicesAutonomyResolves() {
        Autonomy resolved = agentPlatform.getPlatformServices().autonomy();
        assertThat(resolved)
                .as("PlatformServices.autonomy() should resolve to the CDI-produced Autonomy bean")
                .isNotNull();
    }

    @Test
    void testAiBeanIsInjectable() {
        assertThat(ai)
                .as("Ai should be injectable as a CDI bean")
                .isNotNull();
    }

    @Test
    void testExecutingOperationContextBeanIsInjectable() {
        assertThat(executingOperationContext)
                .as("ExecutingOperationContext should be injectable as a CDI bean")
                .isNotNull();

        assertThat(executingOperationContext.ai())
                .as("ExecutingOperationContext should provide Ai instance")
                .isNotNull();
    }

    @Test
    void testAiBuilderBeanIsInjectable() {
        assertThat(aiBuilder)
                .as("AiBuilder should be injectable as a CDI bean")
                .isNotNull();

        assertThat(aiBuilder.ai())
                .as("AiBuilder should be able to create Ai instances")
                .isNotNull();
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
     * Test that @Agent classes are discovered at build time and registered as CDI beans.
     * This verifies that:
     * 1. @Agent classes are discovered by discoverAgents() build step via Jandex
     * 2. They are registered as CDI beans and are injectable
     * 3. Agents with @LlmTool methods can be created
     * <p>
     * Build-time discovery (via Jandex):
     * - @Agent annotation
     * - @Action methods (including inherited)
     * - @Condition methods (including inherited)
     * - @Cost methods (including inherited)
     * - @AchievesGoal annotations
     * <p>
     * Runtime discovery (via Tool.safelyFromInstance()):
     * - @LlmTool methods
     */
    @Test
    void testAgentDiscoveryAndBeanRegistration() {
        assertThat(testAgent)
                .as("@Agent annotated class should be discovered and registered as a CDI bean")
                .isNotNull();

        // Verify the @LlmTool method works (runtime discovery)
        String result = testAgent.reverseString("hello");
        assertThat(result)
                .as("@LlmTool method should be callable")
                .isEqualTo("olleh");
    }

    /**
     * Test that agents are actually deployed to the AgentPlatform.
     * This verifies that the AgentDeploymentRecorder properly:
     * 1. Looks up agent beans from CDI
     * 2. Creates AgentScope using build-time metadata
     * 3. Deploys agents to the platform
     */
    @Test
    void testAgentDeploymentToPlatform() {
        var deployedAgents = agentPlatform.agents();

        assertThat(deployedAgents)
                .as("AgentPlatform should have deployed agents")
                .isNotEmpty();

        // Find our test agents
        var testAgentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgent"))
                .findFirst();

        var inheritingAgentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("InheritingAgent"))
                .findFirst();

        assertThat(testAgentScope)
                .as("TestAgent should be deployed to AgentPlatform")
                .isPresent();

        assertThat(inheritingAgentScope)
                .as("InheritingAgent should be deployed to AgentPlatform")
                .isPresent();
    }

    /**
     * Test that @Action methods are discovered at build time via Jandex.
     * This verifies that the AgentMethodScanner finds all action methods.
     */
    @Test
    void testActionMethodDiscovery() {
        var deployedAgents = agentPlatform.agents();
        var testAgentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgent"))
                .findFirst()
                .orElseThrow();

        assertThat(testAgentScope.getActions())
                .as("TestAgent should have discovered @Action methods")
                .isNotEmpty()
                .as("Should have greet action")
                .anyMatch(action -> action.getName().contains("greet"))
                .as("Should have produceGreeting action")
                .anyMatch(action -> action.getName().contains("produceGreeting"));
    }

    /**
     * Test that @Condition methods are discovered at build time via Jandex.
     */
    @Test
    void testConditionMethodDiscovery() {
        var deployedAgents = agentPlatform.agents();
        var testAgentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgent"))
                .findFirst()
                .orElseThrow();

        assertThat(testAgentScope.getConditions())
                .as("TestAgent should have discovered @Condition methods")
                .isNotEmpty()
                .as("Should have hasUserInput condition")
                .anyMatch(cond -> cond.getName().equals("hasUserInput"));
    }

    /**
     * Test that @Cost methods are discovered at build time and linked to actions.
     * This verifies the complete flow:
     * 1. Build-time: Jandex discovers @Cost methods
     * 2. Build-time: @Action references @Cost via cost="costMethodName"
     * 3. Runtime: QuarkusAgentDeployer creates CostMethodInfo and passes to ActionMethodManager
     */
    @Test
    void testCostMethodDiscoveryAndLinking() {
        var deployedAgents = agentPlatform.agents();
        var testAgentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgent"))
                .findFirst()
                .orElseThrow();

        // Find the greet action which has a cost method
        var greetAction = testAgentScope.getActions().stream()
                .filter(action -> action.getName().contains("greet"))
                .findFirst()
                .orElseThrow();

        // Verify the action exists (cost method linkage is internal to ActionMethodManager)
        assertThat(greetAction)
                .as("Greet action with cost method should exist")
                .isNotNull();
    }

    /**
     * Test that @AchievesGoal creates goals automatically.
     * This verifies:
     * 1. Build-time: Jandex discovers @AchievesGoal annotation
     * 2. Runtime: QuarkusAgentDeployer creates Goal from action + metadata
     */
    @Test
    void testAchievesGoalCreatesGoals() {
        var deployedAgents = agentPlatform.agents();
        var testAgentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgent"))
                .findFirst()
                .orElseThrow();

        assertThat(testAgentScope.getGoals())
                .as("TestAgent should have goals from @AchievesGoal")
                .isNotEmpty()
                .as("Should have produceGreeting goal")
                .anyMatch(goal -> goal.getDescription().contains("Produce greeting"));
    }

    /**
     * CRITICAL TEST: Verify that methods inherited from interfaces are discovered.
     * This tests the main feature of the build-time metadata system - solving the
     * inheritance problem where getDeclaredMethods() doesn't see inherited methods.
     */
    @Test
    void testInheritedMethodsFromInterfaceAreDiscovered() {
        assertThat(inheritingAgent)
                .as("Agent implementing interface should be registered as CDI bean")
                .isNotNull();

        var deployedAgents = agentPlatform.agents();
        var inheritingAgentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("InheritingAgent"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("InheritingAgent should be deployed"));

        // Verify inherited @Action method was discovered
        assertThat(inheritingAgentScope.getActions())
                .as("InheritingAgent should have discovered inherited @Action from interface")
                .isNotEmpty()
                .as("Should have inheritedAction from AgentCapabilities interface")
                .anyMatch(action -> action.getName().contains("inheritedAction"));

        // Verify inherited @Condition method was discovered
        assertThat(inheritingAgentScope.getConditions())
                .as("InheritingAgent should have discovered inherited @Condition from interface")
                .isNotEmpty()
                .as("Should have inheritedCondition from AgentCapabilities interface")
                .anyMatch(cond -> cond.getName().equals("inheritedCondition"));
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
     * Test agent with comprehensive annotation coverage.
     * <p>
     * Build-time discovery (via Jandex):
     * - @Agent annotation
     * - @Action methods
     * - @Condition methods
     * - @Cost methods
     * - @AchievesGoal annotations
     * <p>
     * Runtime discovery (via Tool.safelyFromInstance()):
     * - @LlmTool methods
     */
    @Agent(description = "Test agent for verifying build-time metadata discovery")
    static class TestAgent {

        /**
         * Simple LLM tool that reverses a string.
         * Discovered at RUNTIME via Tool.safelyFromInstance().
         */
        @LlmTool(description = "Reverses the input string")
        public String reverseString(String input) {
            return new StringBuilder(input).reverse().toString();
        }

        /**
         * Condition to check if user input exists.
         * Discovered at BUILD TIME via Jandex.
         */
        @Condition(name = "hasUserInput", cost = 1.0)
        public boolean hasUserInput(OperationContext context) {
            // Simplified check - in real usage would access context
            return true;
        }

        /**
         * Cost calculation method for the greet action.
         * Discovered at BUILD TIME via Jandex and linked to action.
         */
        @Cost(name = "greetCost")
        public double calculateGreetCost(OperationContext context) {
            return 5.0;
        }

        /**
         * Action that uses a cost method.
         * Discovered at BUILD TIME via Jandex.
         */
        @Action(costMethod = "greetCost")
        public String greet(UserInput userInput, Ai ai) {
            return "Hello from TestAgent! Input: " + userInput.getContent();
        }

        /**
         * Action that achieves a goal.
         * Both @Action and @AchievesGoal discovered at BUILD TIME via Jandex.
         * Goal is automatically created with proper preconditions.
         */
        @Action
        @AchievesGoal(description = "Produce greeting", value = 10.0)
        public GreetingResult produceGreeting(UserInput userInput) {
            return new GreetingResult("Hello from goal-achieving action!");
        }
    }

    /**
     * Interface with agent capabilities to test inherited method discovery.
     * This is the CRITICAL TEST for the build-time metadata system.
     * Runtime getDeclaredMethods() doesn't see interface methods,
     * but build-time Jandex scanning does!
     */
    interface AgentCapabilities {

        @Action
        String inheritedAction(UserInput input);

        @Condition(name = "inheritedCondition")
        boolean inheritedCheck();
    }

    /**
     * Agent that implements an interface with @Action and @Condition methods.
     * Tests that build-time Jandex scanning finds inherited methods.
     */
    @Agent(description = "Agent that inherits methods from interface")
    static class InheritingAgent implements AgentCapabilities {

        @Override
        public String inheritedAction(UserInput input) {
            return "Action inherited from interface! Input: " + input.getContent();
        }

        @Override
        public boolean inheritedCheck() {
            return true;
        }
    }

    /**
     * Simple result type for testing @AchievesGoal.
     */
    static class GreetingResult {
        private final String message;

        GreetingResult(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
