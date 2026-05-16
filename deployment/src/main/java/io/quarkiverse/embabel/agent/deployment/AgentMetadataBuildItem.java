package io.quarkiverse.embabel.agent.deployment;

import java.util.List;
import java.util.Map;

import io.quarkiverse.embabel.agent.runtime.ActionMethodBuildInfo;
import io.quarkiverse.embabel.agent.runtime.ConditionMethodBuildInfo;
import io.quarkiverse.embabel.agent.runtime.CostMethodBuildInfo;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Build item containing comprehensive metadata for all discovered agents.
 * Captured at build time via Jandex to eliminate redundant runtime reflection.
 * <p>
 * This build item stores method-level metadata for:
 * <ul>
 * <li>@Action methods (including inherited from interfaces/superclasses)</li>
 * <li>@Condition methods</li>
 * <li>@Cost methods</li>
 * </ul>
 * <p>
 * At runtime, QuarkusAgentDeployer looks up this metadata instead of scanning,
 * which solves the inheritance problem (getDeclaredMethods() doesn't see inherited methods).
 */
public final class AgentMetadataBuildItem extends SimpleBuildItem {

    private final Map<String, List<ActionMethodBuildInfo>> actionMethodsByAgent;
    private final Map<String, List<ConditionMethodBuildInfo>> conditionMethodsByAgent;
    private final Map<String, List<CostMethodBuildInfo>> costMethodsByAgent;

    public AgentMetadataBuildItem(
            Map<String, List<ActionMethodBuildInfo>> actionMethodsByAgent,
            Map<String, List<ConditionMethodBuildInfo>> conditionMethodsByAgent,
            Map<String, List<CostMethodBuildInfo>> costMethodsByAgent) {
        this.actionMethodsByAgent = actionMethodsByAgent;
        this.conditionMethodsByAgent = conditionMethodsByAgent;
        this.costMethodsByAgent = costMethodsByAgent;
    }

    /**
     * Get all @Action methods for a specific agent class.
     *
     * @param agentClassName fully qualified agent class name
     * @return list of action method metadata, empty if none found
     */
    public List<ActionMethodBuildInfo> getActionMethods(String agentClassName) {
        return actionMethodsByAgent.getOrDefault(agentClassName, List.of());
    }

    /**
     * Get all @Condition methods for a specific agent class.
     *
     * @param agentClassName fully qualified agent class name
     * @return list of condition method metadata, empty if none found
     */
    public List<ConditionMethodBuildInfo> getConditionMethods(String agentClassName) {
        return conditionMethodsByAgent.getOrDefault(agentClassName, List.of());
    }

    /**
     * Get all @Cost methods for a specific agent class.
     *
     * @param agentClassName fully qualified agent class name
     * @return list of cost method metadata, empty if none found
     */
    public List<CostMethodBuildInfo> getCostMethods(String agentClassName) {
        return costMethodsByAgent.getOrDefault(agentClassName, List.of());
    }

    /**
     * Find a specific @Cost method by its name.
     *
     * @param agentClassName fully qualified agent class name
     * @param costName the name from @Cost(name = "...")
     * @return cost method metadata, or null if not found
     */
    public CostMethodBuildInfo findCostMethod(String agentClassName, String costName) {
        return getCostMethods(agentClassName).stream()
                .filter(cost -> cost.getCostName().equals(costName))
                .findFirst()
                .orElse(null);
    }
}
