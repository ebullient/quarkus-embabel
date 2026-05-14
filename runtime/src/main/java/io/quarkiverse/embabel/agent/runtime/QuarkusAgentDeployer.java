package io.quarkiverse.embabel.agent.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import com.embabel.agent.api.annotation.support.DefaultActionMethodManager;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.Action;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.ComputedBooleanCondition;
import com.embabel.agent.core.Condition;
import com.embabel.agent.core.Goal;

/**
 * Quarkus-native replacement for {@code AgentMetadataReader}.
 * <p>
 * {@code AgentMetadataReader} calls Spring AI's {@code ToolCallbacks.from()} internally,
 * which throws {@code IllegalAccessError} in Quarkus due to JVM module visibility restrictions.
 * This class produces the same {@link AgentScope} result using only Embabel's
 * framework-agnostic SPIs, none of which touch Spring AI:
 * <ul>
 * <li>{@link Tool#safelyFromInstance(Object)} — scans for {@code @LlmTool} methods</li>
 * <li>{@link DefaultActionMethodManager} — builds {@link com.embabel.agent.core.Action} objects</li>
 * <li>{@link ComputedBooleanCondition} — wraps {@code @Condition} methods</li>
 * <li>{@link Agent} secondary constructor — assembles the final {@link AgentScope}</li>
 * </ul>
 * <p>
 * Goals are automatically derived from {@code @Action} method return types.
 * Each distinct non-void return type creates a goal for producing that type.
 * <p>
 * MVP scope: {@code @AchievesGoal}, {@code @Cost} methods, and {@code @State} unrolling
 * are deferred for future implementation.
 */
class QuarkusAgentDeployer {

    private static final Logger logger = Logger.getLogger(QuarkusAgentDeployer.class);
    private final DefaultActionMethodManager actionMethodManager = new DefaultActionMethodManager();
    private final MethodDefinedOperationNameGenerator nameGenerator = new MethodDefinedOperationNameGenerator();

    /**
     * Builds an {@link AgentScope} from a CDI bean instance without invoking Spring AI.
     *
     * @param agentClass the actual agent class (not a CDI proxy)
     * @param agentInstance the CDI bean instance to bind action and condition methods to
     * @param goalActionInfos goal action metadata with @AchievesGoal annotation (discovered at build time via Jandex)
     * @return a fully-wired {@link AgentScope}, or {@code null} if the class has no {@code @Agent}
     */
    @SuppressWarnings("unchecked")
    AgentScope createAgentScope(Class<?> agentClass, Object agentInstance, Set<GoalActionInfo> goalActionInfos) {
        com.embabel.agent.api.annotation.Agent agentAnnotation = agentClass
                .getAnnotation(com.embabel.agent.api.annotation.Agent.class);
        if (agentAnnotation == null) {
            logger.warnf("Class %s has no @Agent annotation — skipping", agentClass.getName());
            return null;
        }

        // @LlmTool discovery — zero Spring AI dependency
        List<Tool> tools = Tool.safelyFromInstance(agentInstance);
        logger.debugf("Discovered %d @LlmTool(s) on %s", tools.size(), agentClass.getSimpleName());

        List<Action> actions = createActions(agentClass, agentInstance, tools);
        Set<Condition> conditions = createConditions(agentClass, agentInstance);
        Set<Goal> goals = createGoalsFromActions(actions, goalActionInfos, agentInstance);

        String name = agentAnnotation.name().isEmpty()
                ? agentClass.getSimpleName()
                : agentAnnotation.name();
        String provider = agentAnnotation.provider().isEmpty()
                ? agentClass.getPackage().getName()
                : agentAnnotation.provider();

        logger.debugf("Building Agent '%s' with %d goal(s), %d action(s) and %d condition(s)",
                name, goals.size(), actions.size(), conditions.size());

        // Build Agent with goals derived from @Action method return types (discovered at build time)
        return new Agent(name, provider, agentAnnotation.version(), agentAnnotation.description(),
                goals, actions, conditions);
    }

    private List<Action> createActions(
            Class<?> agentClass, Object agentInstance, List<Tool> tools) {
        List<Action> actions = new ArrayList<>();
        for (Method method : agentClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(com.embabel.agent.api.annotation.Action.class)) {
                continue;
            }
            try {
                // Raw type: passing empty map is safe — cost method resolution is a no-op
                Action action = actionMethodManager.createAction(
                        method, agentInstance, tools, Map.of());
                actions.add(action);
                logger.debugf("Registered @Action: %s.%s", agentClass.getSimpleName(), method.getName());
            } catch (Exception e) {
                logger.warnf("Failed to create action from %s.%s: %s",
                        agentClass.getSimpleName(), method.getName(), e.getMessage());
            }
        }
        return actions;
    }

    private Set<Condition> createConditions(Class<?> agentClass, Object agentInstance) {
        Set<Condition> conditions = new LinkedHashSet<>();
        for (Method method : agentClass.getDeclaredMethods()) {
            com.embabel.agent.api.annotation.Condition ann = method
                    .getAnnotation(com.embabel.agent.api.annotation.Condition.class);
            if (ann == null) {
                continue;
            }
            String condName = ann.name().isEmpty()
                    ? agentClass.getSimpleName() + "." + method.getName()
                    : ann.name();
            double cost = ann.cost();
            method.setAccessible(true);

            conditions.add(new ComputedBooleanCondition(condName, cost,
                    (context, condition) -> invokeConditionMethod(method, agentInstance, context)));
            logger.debugf("Registered @Condition: %s.%s", agentClass.getSimpleName(), method.getName());
        }
        return conditions;
    }

    /**
     * Creates goals from actions with @AchievesGoal annotation.
     * Matches Spring Boot's AgentMetadataReader.createGoalFromActionMethod() behavior.
     * <p>
     * For each action with @AchievesGoal, creates a goal with:
     * <ul>
     * <li>Name: generated using nameGenerator (e.g., "com.example.Agent.methodName")</li>
     * <li>Description: from @AchievesGoal annotation</li>
     * <li>Input: the action's output binding</li>
     * <li>Preconditions: hasRun(action) + all action preconditions (ensures action has executed and its requirements were
     * met)</li>
     * </ul>
     *
     * @param actions list of actions created for this agent
     * @param goalActionInfos goal action metadata with @AchievesGoal (discovered at build time via Jandex)
     * @param agentInstance the agent instance for name generation
     * @return set of goals for @AchievesGoal-annotated actions
     */
    private Set<Goal> createGoalsFromActions(List<Action> actions, Set<GoalActionInfo> goalActionInfos,
            Object agentInstance) {
        Set<Goal> goals = new LinkedHashSet<>();
        ClassLoader runtimeClassLoader = Thread.currentThread().getContextClassLoader();

        // Build a map of action names to GoalActionInfo for quick lookup
        Map<String, GoalActionInfo> infoByActionName = new java.util.HashMap<>();
        for (GoalActionInfo info : goalActionInfos) {
            infoByActionName.put(info.getFullyQualifiedActionName(), info);
        }

        logger.debugf("Available @AchievesGoal actions from build time: %s", infoByActionName.keySet());

        for (Action action : actions) {
            String actionName = action.getName();
            logger.debugf("Checking runtime action: %s", actionName);

            // Check if this action has @AchievesGoal annotation
            GoalActionInfo goalInfo = infoByActionName.get(actionName);
            if (goalInfo == null) {
                logger.debugf("No @AchievesGoal annotation found for action: %s", actionName);
                continue;
            }

            logger.debugf("Found @AchievesGoal for action %s: %s", actionName, goalInfo.getDescription());

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
                // Extract method name from fully qualified action name: com.example.Agent.methodName -> methodName
                String methodName = actionName.substring(actionName.lastIndexOf('.') + 1);
                String goalName = nameGenerator.generateName(agentInstance, methodName);

                // Create goal with hasRun precondition + action's preconditions
                // Matches Spring Boot's: pre = setOf(Rerun.hasRunCondition(action)) + goalPreconditions
                String hasRunPrecondition = com.embabel.agent.core.support.Rerun.INSTANCE
                        .hasRunCondition(action);

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
                // createInstance signature: createInstance(description, type, name, tags, examples)
                Goal goal = Goal.createInstance(
                        goalInfo.getDescription(), // description from @AchievesGoal
                        outputType, // satisfiedBy type
                        goalName) // name from nameGenerator
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
