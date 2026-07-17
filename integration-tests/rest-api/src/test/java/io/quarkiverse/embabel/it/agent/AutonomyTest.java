package io.quarkiverse.embabel.it.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.embabel.agent.api.common.autonomy.AgentProcessExecution;
import com.embabel.agent.api.common.autonomy.Autonomy;
import com.embabel.agent.api.common.autonomy.GoalChoiceApprover;
import com.embabel.agent.api.common.autonomy.GoalSelectionOptions;
import com.embabel.agent.api.common.autonomy.NoAgentFound;
import com.embabel.agent.api.common.autonomy.NoGoalFound;
import com.embabel.agent.api.common.autonomy.ProcessExecutionException;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.domain.io.UserInput;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AutonomyTest {

    @Inject
    AgentPlatform agentPlatform;

    @Inject
    Autonomy autonomy;

    @BeforeEach
    void resetRanker() {
        TestFakeRanker.reset();
    }

    @Test
    void shouldSelectFinalResultAgent() throws ProcessExecutionException {
        TestFakeRanker.favor("FinalResult");

        AgentProcessExecution result = autonomy.chooseAndRunAgent("test intent", new ProcessOptions());

        assertThat(result.getOutput()).isInstanceOf(EdgeCaseAgent.FinalResult.class);
        EdgeCaseAgent.FinalResult finalResult = (EdgeCaseAgent.FinalResult) result.getOutput();
        assertThat(finalResult.data()).isEqualTo("prepared-test intent");
        assertThat(finalResult.count()).isEqualTo(42);
    }

    @Test
    void shouldSelectAlternateResultAgent() throws ProcessExecutionException {
        TestFakeRanker.favor("AlternateResult");

        AgentProcessExecution result = autonomy.chooseAndRunAgent("test intent", new ProcessOptions());

        assertThat(result.getOutput()).isInstanceOf(EdgeCaseAgent.AlternateResult.class);
        EdgeCaseAgent.AlternateResult alternateResult = (EdgeCaseAgent.AlternateResult) result.getOutput();
        assertThat(alternateResult.message()).isEqualTo("alternate-test intent");
    }

    @Test
    void shouldThrowNoAgentFoundWhenBelowThreshold() {
        TestFakeRanker.scoreAll(0.0);

        assertThatThrownBy(() -> autonomy.chooseAndRunAgent("test intent", new ProcessOptions()))
                .isInstanceOf(NoAgentFound.class);
    }

    // Open mode: chooseAndAccomplishGoal

    @Test
    void shouldAccomplishGoalForFinalResult() throws ProcessExecutionException {
        TestFakeRanker.favor("Final result");

        AgentProcessExecution result = autonomy.chooseAndAccomplishGoal(
                new ProcessOptions(),
                GoalChoiceApprover.Companion.getAPPROVE_ALL(),
                agentPlatform,
                Map.of("userInput", new UserInput("test goal")),
                new GoalSelectionOptions());

        assertThat(result.getOutput()).isInstanceOf(EdgeCaseAgent.FinalResult.class);
        EdgeCaseAgent.FinalResult finalResult = (EdgeCaseAgent.FinalResult) result.getOutput();
        assertThat(finalResult.data()).isEqualTo("prepared-test goal");
        assertThat(finalResult.count()).isEqualTo(42);
    }

    @Test
    void shouldThrowNoGoalFoundWhenBelowThreshold() {
        TestFakeRanker.scoreAll(0.0);

        assertThatThrownBy(() -> autonomy.chooseAndAccomplishGoal(
                new ProcessOptions(),
                GoalChoiceApprover.Companion.getAPPROVE_ALL(),
                agentPlatform,
                Map.of("userInput", new UserInput("test goal")),
                new GoalSelectionOptions()))
                .isInstanceOf(NoGoalFound.class);
    }
}
