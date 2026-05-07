package io.quarkiverse.embabel.agent.deployment;

import java.util.List;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Build item that carries the list of discovered agent class names from build time to runtime.
 * <p>
 * This build item is produced by {@link EmbabelProcessor#discoverAgents} and consumed by
 * {@link EmbabelProcessor#deployAgents} to pass agent class names to the runtime recorder.
 */
public final class AgentClassesBuildItem extends SimpleBuildItem {

    private final List<String> agentClassNames;

    public AgentClassesBuildItem(List<String> agentClassNames) {
        this.agentClassNames = agentClassNames;
    }

    public List<String> getAgentClassNames() {
        return agentClassNames;
    }
}