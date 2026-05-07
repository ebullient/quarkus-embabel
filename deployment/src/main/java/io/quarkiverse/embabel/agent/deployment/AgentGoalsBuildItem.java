package io.quarkiverse.embabel.agent.deployment;

import java.util.Map;
import java.util.Set;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Build item that carries agent goal metadata from build time to runtime.
 * <p>
 * Maps agent class names to their goal return type class names, discovered by scanning
 * {@code @Action} method return types at build time using Jandex.
 * <p>
 * This approach avoids runtime reflection and classloader issues with nested classes.
 */
public final class AgentGoalsBuildItem extends SimpleBuildItem {

    private final Map<String, Set<String>> agentGoals;

    /**
     * @param agentGoals map of agent class name to set of goal return type class names
     */
    public AgentGoalsBuildItem(Map<String, Set<String>> agentGoals) {
        this.agentGoals = agentGoals;
    }

    public Map<String, Set<String>> getAgentGoals() {
        return agentGoals;
    }
}
