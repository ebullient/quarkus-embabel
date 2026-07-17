package io.quarkiverse.embabel.it.agent;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.embabel.agent.api.common.AgentFunction;
import com.embabel.agent.api.common.AgentPlatformTypedOps;
import com.embabel.agent.api.common.TypedOps;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;

import io.quarkiverse.embabel.it.agent.EdgeCaseAgent.AlternateResult;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that QuarkusAgentPlatform supports dynamic agent creation
 * via createAgent(), a code path not exercised by other tests.
 */
@QuarkusTest
class TypedOpsIntegrationTest {

    @Inject
    AgentPlatform agentPlatform;

    @Test
    void shouldCreateAndRunDynamicGoalAgent() {
        TypedOps typedOps = new AgentPlatformTypedOps(agentPlatform);
        AgentFunction<UserInput, AlternateResult> fn = typedOps.asFunction(AlternateResult.class);
        AlternateResult result = fn.apply(new UserInput("world"), new ProcessOptions());

        assertThat(result).isNotNull();
        assertThat(result.message()).isEqualTo("alternate-world");
    }
}
