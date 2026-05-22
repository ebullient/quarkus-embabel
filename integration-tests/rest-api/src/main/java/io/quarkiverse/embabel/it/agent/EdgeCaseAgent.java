package io.quarkiverse.embabel.it.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.domain.io.UserInput;

/**
 * Test agents for @AchievesGoal processing following the Embabel framework pattern.
 * <p>
 * The Embabel framework expects:
 * <ul>
 * <li>Each @Agent class has ONE @AchievesGoal method</li>
 * <li>Multiple agents can compete for the same result type</li>
 * <li>AgentInvocation.create() selects which agent to use based on result type</li>
 * </ul>
 * <p>
 * Tests:
 * <ul>
 * <li>Action chaining with intermediate types (no @AchievesGoal on intermediate)</li>
 * <li>Multiple agents producing different result types</li>
 * </ul>
 */
public class EdgeCaseAgent {

    /**
     * Intermediate result type - produced by action without @AchievesGoal.
     */
    public record IntermediateData(String value) {
    }

    /**
     * Final result type.
     */
    public record FinalResult(String data, int count) {
    }

    /**
     * Another final result type.
     */
    public record AlternateResult(String message) {
    }

    /**
     * Agent that produces FinalResult through action chaining.
     * <p>
     * This agent demonstrates:
     * <ul>
     * <li>Intermediate action without @AchievesGoal (prepareData)</li>
     * <li>Terminal action with @AchievesGoal (achieveFinal)</li>
     * <li>GOAP chaining actions to reach the goal</li>
     * </ul>
     */
    @Agent(description = "Produces FinalResult through data preparation chain")
    public static class FinalResultAgent {

        /**
         * Intermediate action - no @AchievesGoal, produces intermediate data.
         * GOAP can use this as part of a chain, but it doesn't create a goal.
         */
        @Action
        public IntermediateData prepareData(UserInput input) {
            return new IntermediateData("prepared-" + input.getContent());
        }

        /**
         * Terminal action - has @AchievesGoal, produces final result by chaining.
         * Tests that @AchievesGoal works with chained actions.
         */
        @AchievesGoal(description = "Final result has been achieved through data preparation")
        @Action
        public FinalResult achieveFinal(UserInput input, IntermediateData data) {
            return new FinalResult(data.value(), 42);
        }
    }

    /**
     * Agent that produces AlternateResult directly.
     * <p>
     * This agent demonstrates a simple single-action goal.
     */
    @Agent(description = "Produces AlternateResult directly")
    public static class AlternateResultAgent {

        /**
         * Terminal action - has @AchievesGoal, produces result directly.
         * Tests single-action agents.
         */
        @AchievesGoal(description = "Alternate result has been achieved")
        @Action
        public AlternateResult achieveAlternate(UserInput input) {
            return new AlternateResult("alternate-" + input.getContent());
        }
    }
}
