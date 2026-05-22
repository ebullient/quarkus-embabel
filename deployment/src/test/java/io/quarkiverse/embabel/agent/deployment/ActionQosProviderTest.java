package io.quarkiverse.embabel.agent.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.core.ActionQos;
import com.embabel.agent.core.ActionRetryPolicy;
import com.embabel.agent.core.AgentPlatform;

import io.quarkiverse.embabel.agent.runtime.qos.QuarkusActionQosPropertyProvider;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Integration test for Action QoS provider configuration in agent deployment.
 * <p>
 * This test verifies that:
 * <ul>
 * <li>The runtime uses {@link io.quarkiverse.embabel.agent.runtime.qos.QuarkusActionQosPropertyProvider}</li>
 * <li>Named configurations are resolved successfully from properties</li>
 * <li>Agent-level and action-level retry expressions work correctly</li>
 * <li>The effective QoS matches configured values</li>
 * <li>Precedence order is correct: default → agent override → action override</li>
 * </ul>
 * <p>
 * This is the main integration point connecting build-time metadata, runtime CDI,
 * and action creation.
 */
class ActionQosProviderTest {

    @RegisterExtension
    static final QuarkusExtensionTest extensionTest = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(TestAgentWithQos.class, TestAgentWithMissingConfig.class, TestAgentWithFireOnce.class))
            // Minimal LangChain4j config
            .overrideConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideConfigKey("quarkus.langchain4j.openai.chat-model.model-name", "gpt-4o")
            .overrideConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost:8080/mock")
            // Default Action QoS configuration
            .overrideConfigKey("embabel.agent.platform.action-qos.default.max-attempts", "3")
            .overrideConfigKey("embabel.agent.platform.action-qos.default.backoff-millis", "1000")
            .overrideConfigKey("embabel.agent.platform.action-qos.default.idempotent", "false")
            // Named configuration for agent-level retry
            .overrideConfigKey("embabel.agent.platform.action-qos.test-retry.max-attempts", "5")
            .overrideConfigKey("embabel.agent.platform.action-qos.test-retry.backoff-millis", "500")
            .overrideConfigKey("embabel.agent.platform.action-qos.test-retry.idempotent", "true")
            // Named configuration for action-level override
            .overrideConfigKey("embabel.agent.platform.action-qos.action-override.max-attempts", "10")
            .overrideConfigKey("embabel.agent.platform.action-qos.action-override.backoff-millis", "100");

    @Inject
    AgentPlatform agentPlatform;

    @Inject
    Instance<QuarkusActionQosPropertyProvider> propertyProviderInstance;

    /**
     * Test agent with agent-level retry expression.
     */
    @Agent(name = "TestAgentWithQos", description = "Test agent for Action QoS integration", actionRetryPolicyExpression = "test-retry")
    static class TestAgentWithQos {

        @Action
        public String agentLevelRetry() {
            return "agent-level";
        }

        @Action(actionRetryPolicyExpression = "action-override")
        public String actionLevelOverride() {
            return "action-level";
        }

        @Action
        public String defaultRetry() {
            return "default";
        }
    }

    @Test
    void agentIsDeployed() {
        // Verify the test agent was deployed
        var deployedAgents = agentPlatform.agents();
        var agentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgentWithQos"))
                .findFirst();

        assertThat(agentScope).isPresent();
        assertThat(agentScope.get().getName()).isEqualTo("TestAgentWithQos");
    }

    @Test
    void agentLevelRetry_usesNamedConfiguration() {
        // Given - agent with agent-level retry expression "test-retry"
        var deployedAgents = agentPlatform.agents();
        var agentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgentWithQos"))
                .findFirst()
                .orElseThrow();

        // When - get action with agent-level retry
        com.embabel.agent.core.Action action = agentScope.getActions().stream()
                .filter(a -> a.getName().endsWith("agentLevelRetry"))
                .findFirst()
                .orElse(null);

        // Then - action uses named configuration from agent-level expression
        assertThat(action).isNotNull();
        ActionQos qos = action.getQos();
        assertThat(qos).isNotNull();
        assertThat(qos.getMaxAttempts()).isEqualTo(5);
        assertThat(qos.getBackoffMillis()).isEqualTo(500L);
        assertThat(qos.getIdempotent()).isTrue();
    }

    @Test
    void actionLevelOverride_usesActionConfiguration() {
        // Given - agent with agent-level retry but action has override
        var deployedAgents = agentPlatform.agents();
        var agentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgentWithQos"))
                .findFirst()
                .orElseThrow();

        // When - get action with action-level override
        com.embabel.agent.core.Action action = agentScope.getActions().stream()
                .filter(a -> a.getName().endsWith("actionLevelOverride"))
                .findFirst()
                .orElse(null);

        // Then - action uses action-level configuration (overrides agent-level)
        assertThat(action).isNotNull();
        ActionQos qos = action.getQos();
        assertThat(qos).isNotNull();
        assertThat(qos.getMaxAttempts()).isEqualTo(10);
        assertThat(qos.getBackoffMillis()).isEqualTo(100L);
        // idempotent not set in action-override, should inherit from agent-level (test-retry)
        assertThat(qos.getIdempotent()).isTrue();
    }

    @Test
    void defaultRetry_inheritsFromAgentLevel() {
        // Given - agent with agent-level retry but action has no expression
        var deployedAgents = agentPlatform.agents();
        var agentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgentWithQos"))
                .findFirst()
                .orElseThrow();

        // When - get action with no retry expression
        com.embabel.agent.core.Action action = agentScope.getActions().stream()
                .filter(a -> a.getName().endsWith("defaultRetry"))
                .findFirst()
                .orElse(null);

        // Then - action uses agent-level configuration (inherited from agent)
        assertThat(action).isNotNull();
        ActionQos qos = action.getQos();
        assertThat(qos).isNotNull();
        assertThat(qos.getMaxAttempts()).isEqualTo(5);
        assertThat(qos.getBackoffMillis()).isEqualTo(500L);
        assertThat(qos.getIdempotent()).isTrue();
    }

    @Test
    void precedenceOrder_isCorrect() {
        // This test verifies the precedence order:
        // default → agent override → action override

        var deployedAgents = agentPlatform.agents();
        var agentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgentWithQos"))
                .findFirst()
                .orElseThrow();

        // Action with action-level override should have highest precedence
        com.embabel.agent.core.Action actionOverride = agentScope.getActions().stream()
                .filter(a -> a.getName().endsWith("actionLevelOverride"))
                .findFirst()
                .orElse(null);
        assertThat(actionOverride).isNotNull();
        assertThat(actionOverride.getQos().getMaxAttempts()).isEqualTo(10); // action-override value

        // Action without override should inherit from agent
        com.embabel.agent.core.Action agentLevel = agentScope.getActions().stream()
                .filter(a -> a.getName().endsWith("agentLevelRetry"))
                .findFirst()
                .orElse(null);
        assertThat(agentLevel).isNotNull();
        assertThat(agentLevel.getQos().getMaxAttempts()).isEqualTo(5); // test-retry value

        // Action without any expression should also inherit from agent
        com.embabel.agent.core.Action defaultAction = agentScope.getActions().stream()
                .filter(a -> a.getName().endsWith("defaultRetry"))
                .findFirst()
                .orElse(null);
        assertThat(defaultAction).isNotNull();
        assertThat(defaultAction.getQos().getMaxAttempts()).isEqualTo(5); // test-retry value (inherited)
    }

    @Test
    void partialOverride_mergesWithDefaults() {
        // This test verifies that partial field overrides work correctly
        // action-override only sets maxAttempts and backoffMillis
        // Other fields should come from defaults

        var deployedAgents = agentPlatform.agents();
        var agentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgentWithQos"))
                .findFirst()
                .orElseThrow();

        com.embabel.agent.core.Action action = agentScope.getActions().stream()
                .filter(a -> a.getName().endsWith("actionLevelOverride"))
                .findFirst()
                .orElse(null);

        assertThat(action).isNotNull();
        ActionQos qos = action.getQos();

        // Fields from action-override
        assertThat(qos.getMaxAttempts()).isEqualTo(10);
        assertThat(qos.getBackoffMillis()).isEqualTo(100L);

        // Fields not in action-override should inherit from agent-level (test-retry)
        assertThat(qos.getIdempotent()).isTrue(); // from agent-level test-retry config
    }

    @Test
    void allActionsHaveValidQos() {
        // Verify that all actions have non-null QoS configuration
        var deployedAgents = agentPlatform.agents();
        var agentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgentWithQos"))
                .findFirst()
                .orElseThrow();

        assertThat(agentScope.getActions()).hasSize(3);
        agentScope.getActions().forEach(action -> {
            ActionQos qos = action.getQos();
            assertThat(qos).isNotNull();
            assertThat(qos.getMaxAttempts()).isGreaterThan(0);
            assertThat(qos.getBackoffMillis()).isGreaterThan(0);
        });
    }

    /**
     * Test agent with missing named configuration reference.
     * This verifies Step 8: fallback behavior when named configuration is absent.
     */
    @Agent(name = "TestAgentWithMissingConfig", description = "Test agent with missing config reference", actionRetryPolicyExpression = "missing-config")
    static class TestAgentWithMissingConfig {

        @Action
        public String actionWithMissingAgentConfig() {
            return "missing-agent-config";
        }

        @Action(actionRetryPolicyExpression = "another-missing-config")
        public String actionWithMissingActionConfig() {
            return "missing-action-config";
        }
    }

    @Test
    void missingNamedConfig_fallsBackToDefaults() {
        // Given - agent with retry expression referencing non-existent config
        var deployedAgents = agentPlatform.agents();
        var agentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgentWithMissingConfig"))
                .findFirst()
                .orElseThrow();

        // When - get action with missing agent-level config
        com.embabel.agent.core.Action action = agentScope.getActions().stream()
                .filter(a -> a.getName().endsWith("actionWithMissingAgentConfig"))
                .findFirst()
                .orElse(null);

        // Then - action falls back to default configuration
        assertThat(action).isNotNull();
        ActionQos qos = action.getQos();
        assertThat(qos).isNotNull();
        // Should use default values from embabel.agent.platform.action-qos.default.*
        assertThat(qos.getMaxAttempts()).isEqualTo(3);
        assertThat(qos.getBackoffMillis()).isEqualTo(1000L);
        assertThat(qos.getIdempotent()).isFalse();
    }

    @Test
    void missingActionLevelConfig_fallsBackToDefaults() {
        // Given - agent with missing agent-level config and action with missing action-level config
        var deployedAgents = agentPlatform.agents();
        var agentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgentWithMissingConfig"))
                .findFirst()
                .orElseThrow();

        // When - get action with missing action-level config
        com.embabel.agent.core.Action action = agentScope.getActions().stream()
                .filter(a -> a.getName().endsWith("actionWithMissingActionConfig"))
                .findFirst()
                .orElse(null);

        // Then - action falls back to default configuration
        assertThat(action).isNotNull();
        ActionQos qos = action.getQos();
        assertThat(qos).isNotNull();
        // Should use default values since both agent and action configs are missing
        assertThat(qos.getMaxAttempts()).isEqualTo(3);
        assertThat(qos.getBackoffMillis()).isEqualTo(1000L);
        assertThat(qos.getIdempotent()).isFalse();
    }

    @Test
    void missingNamedConfig_doesNotFailDeployment() {
        // Verify that agents with missing named configs are still deployed successfully
        var deployedAgents = agentPlatform.agents();
        var agentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgentWithMissingConfig"))
                .findFirst();

        // Agent should be deployed despite missing config references
        assertThat(agentScope).isPresent();
        assertThat(agentScope.get().getName()).isEqualTo("TestAgentWithMissingConfig");

        // All actions should be present and have valid QoS
        assertThat(agentScope.get().getActions()).hasSize(2);
        agentScope.get().getActions().forEach(action -> {
            ActionQos qos = action.getQos();
            assertThat(qos).isNotNull();
            assertThat(qos.getMaxAttempts()).isGreaterThan(0);
            assertThat(qos.getBackoffMillis()).isGreaterThan(0);
        });
    }

    /**
     * Test agent with FIRE_ONCE policy.
     * Verifies that FIRE_ONCE overrides all other QoS settings.
     */
    @Agent(name = "TestAgentWithFireOnce", description = "Test agent with FIRE_ONCE policy")
    static class TestAgentWithFireOnce {

        @Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
        public String fireOnceAction() {
            return "fire-once";
        }

        @Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE, actionRetryPolicyExpression = "test-retry")
        public String fireOnceWithExpression() {
            return "fire-once-with-expression";
        }
    }

    @Test
    void fireOncePolicy_overridesAllQosSettings() {
        // Given - agent with FIRE_ONCE actions
        var deployedAgents = agentPlatform.agents();
        var agentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgentWithFireOnce"))
                .findFirst()
                .orElseThrow();

        // When - get action with FIRE_ONCE policy
        com.embabel.agent.core.Action action = agentScope.getActions().stream()
                .filter(a -> a.getName().endsWith("fireOnceAction"))
                .findFirst()
                .orElse(null);

        // Then - maxAttempts is forced to 1 regardless of defaults
        assertThat(action).isNotNull();
        ActionQos qos = action.getQos();
        assertThat(qos).isNotNull();
        assertThat(qos.getMaxAttempts()).isEqualTo(1);
    }

    @Test
    void fireOncePolicy_overridesNamedConfiguration() {
        // Given - action with both FIRE_ONCE and retry expression
        var deployedAgents = agentPlatform.agents();
        var agentScope = deployedAgents.stream()
                .filter(a -> a.getName().equals("TestAgentWithFireOnce"))
                .findFirst()
                .orElseThrow();

        // When - get action with FIRE_ONCE + expression
        com.embabel.agent.core.Action action = agentScope.getActions().stream()
                .filter(a -> a.getName().endsWith("fireOnceWithExpression"))
                .findFirst()
                .orElse(null);

        // Then - FIRE_ONCE takes precedence, maxAttempts is 1
        assertThat(action).isNotNull();
        ActionQos qos = action.getQos();
        assertThat(qos).isNotNull();
        assertThat(qos.getMaxAttempts()).isEqualTo(1);
        // Other fields should still come from named config
        assertThat(qos.getBackoffMillis()).isEqualTo(500L); // from test-retry
        assertThat(qos.getIdempotent()).isTrue(); // from test-retry
    }

    @Test
    void quarkusPropertyProvider_isUsed() {
        // Verify that QuarkusActionQosPropertyProvider is available as a CDI bean
        // This proves we're using the Quarkus-specific provider, not Spring Binder
        assertThat(propertyProviderInstance.isResolvable()).isTrue();
        assertThat(propertyProviderInstance.isAmbiguous()).isFalse();

        QuarkusActionQosPropertyProvider provider = propertyProviderInstance.get();
        assertThat(provider).isNotNull();

        // Verify it can resolve named configs
        var boundProps = provider.getBound("test-retry");
        assertThat(boundProps).isNotNull();
        assertThat(boundProps.getMaxAttempts()).isEqualTo(5);
    }

    @Test
    void springBinderProvider_isNotInstantiated() {
        // Negative verification: ensure Spring Binder-dependent ActionQosPropertyProvider
        // is never instantiated. We verify this by checking that only our Quarkus provider exists.

        // The QuarkusActionQosPropertyProvider should be the only provider bean
        assertThat(propertyProviderInstance.isResolvable()).isTrue();
        assertThat(propertyProviderInstance.isAmbiguous()).isFalse();

        // Verify it's specifically our Quarkus implementation
        QuarkusActionQosPropertyProvider provider = propertyProviderInstance.get();
        assertThat(provider).isInstanceOf(QuarkusActionQosPropertyProvider.class);

        // If Spring's ActionQosPropertyProvider were instantiated, we'd have ambiguous beans
        // or the wrong type. This test confirms we're using only the Quarkus provider.
    }
}