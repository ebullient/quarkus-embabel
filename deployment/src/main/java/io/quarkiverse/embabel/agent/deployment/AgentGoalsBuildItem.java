package io.quarkiverse.embabel.agent.deployment;

import java.util.Map;
import java.util.Set;

import io.quarkiverse.embabel.agent.runtime.GoalActionInfo;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Build item that carries agent goal metadata discovered during the build process.
 * <p>
 * Tracks which {@code @Action} methods have {@code @AchievesGoal} annotations,
 * which is critical for GOAP planning to understand which actions are terminal (goal-achieving).
 * <p>
 * At runtime, {@link io.quarkiverse.embabel.agent.runtime.QuarkusAgentDeployer} uses this
 * information to create goals for each @AchievesGoal-annotated action, matching the behavior
 * of Spring Boot's {@code AgentMetadataReader.createGoalFromActionMethod()}.
 */
public final class AgentGoalsBuildItem extends SimpleBuildItem {

    private final Map<String, Set<GoalActionInfo>> agentGoalActions;

    /**
     * @param agentGoalActions map of agent class name to set of goal action metadata
     */
    public AgentGoalsBuildItem(Map<String, Set<GoalActionInfo>> agentGoalActions) {
        this.agentGoalActions = agentGoalActions;
    }

    public Map<String, Set<GoalActionInfo>> getAgentGoalActions() {
        return agentGoalActions;
    }
}
