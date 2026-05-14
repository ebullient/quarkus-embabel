package io.quarkiverse.embabel.it.agent;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;

import io.quarkiverse.embabel.it.agent.EdgeCaseAgent.AlternateResult;
import io.quarkiverse.embabel.it.agent.EdgeCaseAgent.FinalResult;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Test @AchievesGoal processing following the Embabel framework pattern.
 * <p>
 * These tests verify that:
 * <ul>
 * <li>Actions with @AchievesGoal create goals</li>
 * <li>Actions without @AchievesGoal can still be used as intermediate steps in action chains</li>
 * <li>Multiple agents with different @AchievesGoal can coexist</li>
 * <li>AgentInvocation.create() selects the correct agent based on result type</li>
 * </ul>
 */
@QuarkusTest
class EdgeCaseAgentTest {

    @Inject
    AgentPlatform agentPlatform;

    @Test
    void shouldHandleActionChainWithIntermediateTypeWithoutAchievesGoal() {
        // When: Request FinalResult - AgentInvocation selects FinalResultAgent
        // GOAP should chain: prepareData() -> achieveFinal()
        // prepareData() has NO @AchievesGoal, but GOAP can still use it as an intermediate step
        AgentInvocation<FinalResult> invocation = AgentInvocation.create(agentPlatform, FinalResult.class);
        FinalResult result = invocation.invoke(new UserInput("test"));

        // Then: Should successfully chain actions even though intermediate lacks @AchievesGoal
        assertThat(result).isNotNull();
        assertThat(result.data()).isEqualTo("prepared-test"); // From prepareData()
        assertThat(result.count()).isEqualTo(42); // From achieveFinal()
    }

    @Test
    void shouldSelectCorrectAgentByResultType() {
        // When: Request AlternateResult - AgentInvocation selects AlternateResultAgent
        AgentInvocation<AlternateResult> invocation = AgentInvocation.create(agentPlatform, AlternateResult.class);
        AlternateResult result = invocation.invoke(new UserInput("test"));

        // Then: Should get result from AlternateResultAgent.achieveAlternate action
        assertThat(result).isNotNull();
        assertThat(result.message()).isEqualTo("alternate-test");
    }
}
