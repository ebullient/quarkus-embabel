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

    /**
     * Builds an {@link AgentScope} from a CDI bean instance without invoking Spring AI.
     *
     * @param agentClass the actual agent class (not a CDI proxy)
     * @param agentInstance the CDI bean instance to bind action and condition methods to
     * @param goalReturnTypeNames class names of goal return types (discovered at build time via Jandex)
     * @return a fully-wired {@link AgentScope}, or {@code null} if the class has no {@code @Agent}
     */
    @SuppressWarnings("unchecked")
    AgentScope createAgentScope(Class<?> agentClass, Object agentInstance, Set<String> goalReturnTypeNames) {
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
        Set<Goal> goals = createGoalsFromReturnTypeNames(goalReturnTypeNames);

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
     * Creates goals from pre-discovered return type class names (from build-time Jandex scan).
     * Avoids runtime reflection and resolves classloader issues with nested classes.
     *
     * @param goalReturnTypeNames class names of goal return types (discovered at build time)
     * @return set of goals derived from return type class names
     */
    private Set<Goal> createGoalsFromReturnTypeNames(Set<String> goalReturnTypeNames) {
        Set<Goal> goals = new LinkedHashSet<>();
        ClassLoader runtimeClassLoader = Thread.currentThread().getContextClassLoader();

        for (String className : goalReturnTypeNames) {
            try {
                // Load the class using the runtime classloader
                Class<?> returnType = runtimeClassLoader.loadClass(className);

                // Create a goal for this return type
                String goalDescription = String.format("Create %s", returnType.getSimpleName());
                Goal goal = Goal.createInstance(goalDescription, returnType);
                goals.add(goal);

                logger.debugf("Created goal for return type: %s", returnType.getSimpleName());
            } catch (ClassNotFoundException e) {
                logger.warnf("Failed to load return type %s for goal creation: %s",
                        className, e.getMessage());
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
