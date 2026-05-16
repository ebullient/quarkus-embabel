package io.quarkiverse.embabel.agent.runtime;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.inject.spi.CDI;

import org.jboss.logging.Logger;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentScope;

import io.quarkus.runtime.annotations.Recorder;

/**
 * Runtime recorder for deploying agent beans to the AgentPlatform.
 * <p>
 * This recorder is invoked at {@link io.quarkus.deployment.annotations.ExecutionTime#RUNTIME_INIT}
 * to deploy all discovered agent beans to the {@link AgentPlatform}. It works by:
 * <ol>
 * <li>Receiving a list of agent class names from the build step</li>
 * <li>Loading each class and looking up the CDI bean instance</li>
 * <li>Creating agent metadata using {@link QuarkusAgentDeployer}</li>
 * <li>Deploying the agent to the {@link AgentPlatform}</li>
 * </ol>
 * <p>
 * Uses {@link QuarkusAgentDeployer} instead of {@code AgentMetadataReader} to avoid
 * the Spring AI classloader issue: {@code AgentMetadataReader} calls
 * {@code ToolCallbacks.from()} which throws {@code IllegalAccessError} in Quarkus.
 *
 * @see io.quarkiverse.embabel.agent.deployment.EmbabelProcessor#deployAgents
 * @see com.embabel.agent.spi.support.AgentScanningPostProcessorDelegate
 */
@Recorder
public class AgentDeploymentRecorder {

    private static final Logger logger = Logger.getLogger(AgentDeploymentRecorder.class);

    /**
     * Deploys all discovered agent beans to the AgentPlatform.
     *
     * @param agentClassNames list of agent class names discovered at build time
     * @param actionMethodsByAgent map of agent class name to action method metadata
     * @param conditionMethodsByAgent map of agent class name to condition method metadata
     * @param costMethodsByAgent map of agent class name to cost method metadata
     */
    public void deployAgents(
            List<String> agentClassNames,
            Map<String, List<ActionMethodBuildInfo>> actionMethodsByAgent,
            Map<String, List<ConditionMethodBuildInfo>> conditionMethodsByAgent,
            Map<String, List<CostMethodBuildInfo>> costMethodsByAgent) {
        if (agentClassNames.isEmpty()) {
            logger.info("No agent beans discovered");
            return;
        }

        logger.infof("Deploying %d agent bean(s)...", agentClassNames.size());

        CDI<Object> cdi = CDI.current();
        AgentPlatform agentPlatform = cdi.select(AgentPlatform.class).get();
        QuarkusAgentDeployer deployer = new QuarkusAgentDeployer();

        int deployedCount = 0;
        for (String className : agentClassNames) {
            try {
                Class<?> agentClass = Thread.currentThread().getContextClassLoader().loadClass(className);
                Object agentBean = cdi.select(agentClass).get();

                // Get comprehensive pre-discovered metadata for this agent
                List<ActionMethodBuildInfo> actionMethods = actionMethodsByAgent.getOrDefault(className, List.of());
                List<ConditionMethodBuildInfo> conditionMethods = conditionMethodsByAgent.getOrDefault(className,
                        List.of());
                List<CostMethodBuildInfo> costMethods = costMethodsByAgent.getOrDefault(className, List.of());

                AgentScope agentScope = deployer.createAgentScope(
                        agentClass,
                        agentBean,
                        actionMethods,
                        conditionMethods,
                        costMethods);

                if (agentScope != null) {
                    agentPlatform.deploy(agentScope);
                    deployedCount++;
                    logger.debugf("Deployed agent: %s (%s) with %d action(s), %d condition(s), %d cost method(s)",
                            agentScope.getName(), className,
                            actionMethods.size(), conditionMethods.size(), costMethods.size());
                } else {
                    logger.warnf("Skipped agent — no metadata created for: %s", className);
                }
            } catch (ClassNotFoundException e) {
                logger.errorf(e, "Agent class not found: %s", className);
            } catch (Exception e) {
                logger.errorf(e, "Failed to deploy agent bean: %s", className);
            }
        }

        logger.infof("Agent deployment complete. Deployed %d of %d agent(s)", deployedCount, agentClassNames.size());
    }
}
