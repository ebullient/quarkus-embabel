package io.quarkiverse.embabel.agent.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import com.embabel.agent.api.annotation.support.ActionMethodManager;
import com.embabel.agent.api.annotation.support.ActionQosPropertyProvider;
import com.embabel.agent.api.annotation.support.CostMethodInfo;
import com.embabel.agent.api.annotation.support.DefaultActionQosProvider;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.Action;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.ComputedBooleanCondition;
import com.embabel.agent.core.Condition;
import com.embabel.agent.core.Goal;
import com.embabel.agent.core.support.Rerun;
import com.embabel.agent.spi.config.spring.AgentPlatformProperties;
import com.embabel.agent.spi.config.spring.AgentPlatformProperties.ActionQosProperties.ActionProperties;

import io.quarkiverse.embabel.agent.runtime.qos.QuarkusActionMethodManager;
import io.quarkiverse.embabel.agent.runtime.qos.QuarkusActionQosPropertyProvider;

/**
 * Quarkus-native replacement for {@code AgentMetadataReader}.
 * <p>
 * Uses build-time Jandex metadata instead of runtime reflection to build agent metadata.
 * This approach:
 * <ul>
 * <li>Solves the inheritance problem - Jandex sees all methods, getDeclaredMethods() doesn't</li>
 * <li>Eliminates redundant annotation scanning - already done at build time</li>
 * <li>Avoids Spring AI classloader issues with ToolCallbacks.from()</li>
 * <li>Uses only Embabel's framework-agnostic SPIs</li>
 * </ul>
 */
class QuarkusAgentDeployer {

    private static final Logger logger = Logger.getLogger(QuarkusAgentDeployer.class);
    private final ActionMethodManager actionMethodManager;
    private final MethodDefinedOperationNameGenerator nameGenerator = new MethodDefinedOperationNameGenerator();

    /**
     * Constructor accepting platform properties and named config provider.
     *
     * @param platformProperties platform properties containing default Action QoS configuration
     * @param propertyProvider Quarkus-backed property provider for named configurations
     */
    QuarkusAgentDeployer(AgentPlatformProperties platformProperties, QuarkusActionQosPropertyProvider propertyProvider) {
        ActionQosPropertyProvider upstreamPropertyProvider = new ActionQosPropertyProvider() {
            @Override
            public ActionProperties getBound(String expr) {
                return propertyProvider.getBound(expr);
            }
        };

        DefaultActionQosProvider qosProvider = new DefaultActionQosProvider(
                platformProperties.getActionQos(),
                upstreamPropertyProvider);

        this.actionMethodManager = new QuarkusActionMethodManager(nameGenerator, qosProvider);
    }

    /**
     * Builds an {@link AgentScope} from a CDI bean instance using build-time metadata.
     *
     * @param agentClass the actual agent class (not a CDI proxy)
     * @param agentInstance the CDI bean instance to bind action and condition methods to
     * @param actionMethods build-time metadata for @Action methods (includes inherited from interfaces/superclasses)
     * @param conditionMethods build-time metadata for @Condition methods
     * @param costMethods build-time metadata for @Cost methods
     * @return a fully-wired {@link AgentScope}, or {@code null} if the class has no {@code @Agent} or {@code @EmbabelComponent}
     */
    AgentScope createAgentScope(
            Class<?> agentClass,
            Object agentInstance,
            List<ActionMethodBuildInfo> actionMethods,
            List<ConditionMethodBuildInfo> conditionMethods,
            List<CostMethodBuildInfo> costMethods) {

        com.embabel.agent.api.annotation.Agent agentAnnotation = agentClass
                .getAnnotation(com.embabel.agent.api.annotation.Agent.class);
        com.embabel.agent.api.annotation.EmbabelComponent componentAnnotation = agentClass
                .getAnnotation(com.embabel.agent.api.annotation.EmbabelComponent.class);

        if (agentAnnotation == null && componentAnnotation == null) {
            logger.warnf("Class %s has no @Agent or @EmbabelComponent annotation — skipping", agentClass.getName());
            return null;
        }

        // Discover tools on the agent instance
        List<Tool> toolsOnInstance = Tool.safelyFromInstance(agentInstance);
        logger.debugf("Discovered %d tool(s) on agent instance %s", toolsOnInstance.size(), agentClass.getSimpleName());

        // Build cost method lookup map
        Map<String, CostMethodInfo> costMethodMap = buildCostMethodMap(agentClass, agentInstance, costMethods);

        // Build actions from metadata
        List<Action> actions = createActionsFromMetadata(
                agentClass, agentInstance, actionMethods, toolsOnInstance, costMethodMap);

        // Build conditions from metadata
        Set<Condition> conditions = createConditionsFromMetadata(agentClass, agentInstance, conditionMethods);

        // Build goals from actions with @AchievesGoal
        Set<Goal> goals = createGoalsFromMetadata(actions, actionMethods, agentInstance);

        // Get agent metadata from @Agent or generate defaults for @EmbabelComponent
        String name;
        String provider;
        String version;
        String description;

        if (agentAnnotation != null) {
            name = agentAnnotation.name().isEmpty() ? agentClass.getSimpleName() : agentAnnotation.name();
            provider = agentAnnotation.provider().isEmpty() ? agentClass.getPackage().getName()
                    : agentAnnotation.provider();
            version = agentAnnotation.version();
            description = agentAnnotation.description();
        } else {
            // @EmbabelComponent - use class name and package
            name = agentClass.getSimpleName();
            provider = agentClass.getPackage().getName();
            version = "1.0.0";
            description = "Component providing reusable actions";
        }

        logger.debugf("Building Agent '%s' with %d goal(s), %d action(s), %d condition(s)",
                name, goals.size(), actions.size(), conditions.size());

        return new Agent(name, provider, version, description, goals, actions, conditions);
    }

    /**
     * Build cost method lookup map from build-time metadata.
     */
    private Map<String, CostMethodInfo> buildCostMethodMap(
            Class<?> agentClass,
            Object agentInstance,
            List<CostMethodBuildInfo> costMethodsMetadata) {

        Map<String, CostMethodInfo> costMethodMap = new HashMap<>();

        for (CostMethodBuildInfo costInfo : costMethodsMetadata) {
            try {
                // Find method on the class where it was declared (may be interface/superclass)
                Class<?> declaringClass = Thread.currentThread().getContextClassLoader()
                        .loadClass(costInfo.getClassName());
                Method method = findMethod(declaringClass, costInfo.getMethodName(), costInfo.getParameters());
                if (method != null) {
                    method.setAccessible(true);
                    costMethodMap.put(costInfo.getCostName(), new CostMethodInfo(method, agentInstance));
                    logger.debugf("Registered @Cost: %s [%s]", costInfo.getMethodName(), costInfo.getCostName());
                } else {
                    logger.warnf("Could not find @Cost method: %s.%s", costInfo.getClassName(),
                            costInfo.getMethodName());
                }
            } catch (Exception e) {
                logger.warnf("Failed to register @Cost method %s: %s", costInfo.getMethodName(), e.getMessage());
            }
        }

        return costMethodMap;
    }

    /**
     * Create actions from build-time metadata instead of scanning at runtime.
     */
    private List<Action> createActionsFromMetadata(
            Class<?> agentClass,
            Object agentInstance,
            List<ActionMethodBuildInfo> actionMethodsMetadata,
            List<Tool> toolsOnInstance,
            Map<String, CostMethodInfo> costMethodMap) {

        List<Action> actions = new ArrayList<>();

        for (ActionMethodBuildInfo actionInfo : actionMethodsMetadata) {
            try {
                // Find method on the class where it was declared (may be interface/superclass)
                Class<?> declaringClass = Thread.currentThread().getContextClassLoader()
                        .loadClass(actionInfo.getClassName());
                Method method = findMethod(declaringClass, actionInfo.getMethodName(), actionInfo.getParameters());
                if (method == null) {
                    logger.warnf("Could not find @Action method: %s.%s", actionInfo.getClassName(),
                            actionInfo.getMethodName());
                    continue;
                }

                method.setAccessible(true);

                // Build cost methods map for this specific action
                Map<String, CostMethodInfo> actionCostMethods = new HashMap<>();
                if (actionInfo.getCostMethodName() != null) {
                    CostMethodInfo costMethod = costMethodMap.get(actionInfo.getCostMethodName());
                    if (costMethod != null) {
                        actionCostMethods.put(actionInfo.getCostMethodName(), costMethod);
                    } else {
                        logger.warnf("@Action %s references unknown @Cost method: %s",
                                actionInfo.getMethodName(), actionInfo.getCostMethodName());
                    }
                }

                Action action = actionMethodManager.createAction(
                        method, agentInstance, toolsOnInstance, actionCostMethods);
                actions.add(action);
                logger.debugf("Registered @Action: %s.%s -> %s", agentClass.getSimpleName(),
                        actionInfo.getMethodName(), actionInfo.getReturnType());
            } catch (Exception e) {
                logger.warnf("Failed to create action from %s.%s: %s",
                        agentClass.getSimpleName(), actionInfo.getMethodName(), e.getMessage());
            }
        }

        return actions;
    }

    /**
     * Create conditions from build-time metadata instead of scanning at runtime.
     */
    private Set<Condition> createConditionsFromMetadata(
            Class<?> agentClass,
            Object agentInstance,
            List<ConditionMethodBuildInfo> conditionMethodsMetadata) {

        Set<Condition> conditions = new LinkedHashSet<>();

        for (ConditionMethodBuildInfo condInfo : conditionMethodsMetadata) {
            try {
                // Find method on the class where it was declared (may be interface/superclass)
                Class<?> declaringClass = Thread.currentThread().getContextClassLoader()
                        .loadClass(condInfo.getClassName());
                Method method = findMethod(declaringClass, condInfo.getMethodName(), condInfo.getParameters());
                if (method == null) {
                    logger.warnf("Could not find @Condition method: %s.%s", condInfo.getClassName(),
                            condInfo.getMethodName());
                    continue;
                }

                method.setAccessible(true);

                conditions.add(new ComputedBooleanCondition(condInfo.getConditionName(), condInfo.getCost(),
                        (context, condition) -> invokeConditionMethod(method, agentInstance, context)));
                logger.debugf("Registered @Condition: %s.%s [%s]", agentClass.getSimpleName(),
                        condInfo.getMethodName(), condInfo.getConditionName());
            } catch (Exception e) {
                logger.warnf("Failed to create condition from %s.%s: %s",
                        agentClass.getSimpleName(), condInfo.getMethodName(), e.getMessage());
            }
        }

        return conditions;
    }

    /**
     * Find a method by name and parameter types from build-time metadata.
     * Searches the full class hierarchy since metadata may reference inherited methods.
     */
    private Method findMethod(Class<?> clazz, String methodName, List<ParameterBuildInfo> parameters) {
        Class<?>[] paramTypes = new Class<?>[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            try {
                paramTypes[i] = Thread.currentThread().getContextClassLoader().loadClass(parameters.get(i).getType());
            } catch (ClassNotFoundException e) {
                logger.warnf("Could not load parameter type: %s", parameters.get(i).getType());
                return null;
            }
        }

        try {
            // Search in class hierarchy - handles inherited methods
            return clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            // Try getDeclaredMethod as fallback for private methods
            try {
                return clazz.getDeclaredMethod(methodName, paramTypes);
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    }

    /**
     * Creates goals from actions with @AchievesGoal annotation.
     * Uses build-time metadata to identify which actions have goals.
     *
     * @param actions list of actions created for this agent
     * @param actionMethodsMetadata build-time metadata containing @AchievesGoal info
     * @param agentInstance the agent instance for name generation
     * @return set of goals for @AchievesGoal-annotated actions
     */
    private Set<Goal> createGoalsFromMetadata(
            List<Action> actions,
            List<ActionMethodBuildInfo> actionMethodsMetadata,
            Object agentInstance) {

        Set<Goal> goals = new LinkedHashSet<>();
        ClassLoader runtimeClassLoader = Thread.currentThread().getContextClassLoader();

        // Build a map of action names to metadata for quick lookup
        Map<String, ActionMethodBuildInfo> metadataByActionName = new HashMap<>();
        for (ActionMethodBuildInfo actionInfo : actionMethodsMetadata) {
            if (actionInfo.isAchievesGoal()) {
                metadataByActionName.put(actionInfo.getFullyQualifiedName(), actionInfo);
            }
        }

        logger.debugf("Available @AchievesGoal actions from build time: %s", metadataByActionName.keySet());

        for (Action action : actions) {
            String actionName = action.getName();
            logger.debugf("Checking runtime action: %s", actionName);

            // Check if this action has @AchievesGoal annotation
            ActionMethodBuildInfo actionInfo = metadataByActionName.get(actionName);
            if (actionInfo == null) {
                logger.debugf("No @AchievesGoal annotation found for action: %s", actionName);
                continue;
            }

            logger.debugf("Found @AchievesGoal for action %s: %s", actionName, actionInfo.getGoalDescription());

            // Get the action's output type to create the goal
            if (action.getOutputs().isEmpty()) {
                logger.warnf("Action %s has @AchievesGoal but no outputs - skipping goal creation", actionName);
                continue;
            }

            try {
                // Extract return type from output binding format "name:type"
                com.embabel.agent.core.IoBinding outputBinding = action.getOutputs().iterator().next();
                String bindingValue = outputBinding.getValue();
                String outputTypeName = bindingValue.contains(":")
                        ? bindingValue.split(":")[1]
                        : bindingValue;

                Class<?> outputType = runtimeClassLoader.loadClass(outputTypeName);

                // Generate goal name using nameGenerator (matches Spring Boot behavior)
                String methodName = actionName.substring(actionName.lastIndexOf('.') + 1);
                String goalName = nameGenerator.generateName(agentInstance, methodName);

                // Create goal with hasRun precondition + action's preconditions
                String hasRunPrecondition = Rerun.INSTANCE.hasRunCondition(action);

                // Exclude action's output from preconditions to avoid circular dependency
                String outputBindingToExclude = "it:" + outputTypeName;
                Set<String> actionPreconditions = action.getPreconditions().keySet().stream()
                        .filter(precondition -> !precondition.equals(outputBindingToExclude))
                        .collect(java.util.stream.Collectors.toSet());

                // Combine hasRun + filtered action preconditions
                Set<String> allPreconditions = new LinkedHashSet<>();
                allPreconditions.add(hasRunPrecondition);
                allPreconditions.addAll(actionPreconditions);

                // Use @AchievesGoal description and generated name
                Goal goal = Goal.createInstance(
                        actionInfo.getGoalDescription(),
                        outputType,
                        goalName)
                        .withPreconditions(allPreconditions.toArray(new String[0]));

                goals.add(goal);

                logger.debugf("Created goal '%s' for @AchievesGoal action %s: %s with %d precondition(s): %s",
                        goalName, methodName, outputType.getSimpleName(),
                        allPreconditions.size(), allPreconditions);
            } catch (ClassNotFoundException e) {
                logger.warnf("Failed to load output type for @AchievesGoal action %s: %s",
                        actionName, e.getMessage());
            }
        }

        return goals;
    }

    private boolean invokeConditionMethod(Method method, Object instance, Object context) {
        try {
            return Boolean.TRUE.equals(method.invoke(instance, context));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.warnf("@Condition %s.%s threw: %s",
                    instance.getClass().getSimpleName(), method.getName(), cause.getMessage());
            return false;
        } catch (IllegalAccessException e) {
            logger.warnf("Cannot invoke @Condition %s.%s: %s",
                    instance.getClass().getSimpleName(), method.getName(), e.getMessage());
            return false;
        }
    }
}
