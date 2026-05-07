package io.quarkiverse.embabel.agent.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.embabel.agent.api.annotation.support.DefaultActionMethodManager;
import com.embabel.agent.api.tool.Tool;
import com.embabel.agent.core.Action;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.ComputedBooleanCondition;
import com.embabel.agent.core.Condition;

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
 * MVP scope: {@code @AchievesGoal}, {@code @Cost} methods, and {@code @State} unrolling
 * are deferred — goals are left as an empty set.
 */
class QuarkusAgentDeployer {

    private static final Logger logger = LoggerFactory.getLogger(QuarkusAgentDeployer.class);

    private final DefaultActionMethodManager actionMethodManager = new DefaultActionMethodManager();

    /**
     * Builds an {@link AgentScope} from a CDI bean instance without invoking Spring AI.
     *
     * @param agentClass the actual agent class (not a CDI proxy)
     * @param agentInstance the CDI bean instance to bind action and condition methods to
     * @return a fully-wired {@link AgentScope}, or {@code null} if the class has no {@code @Agent}
     */
    @SuppressWarnings("unchecked")
    AgentScope createAgentScope(Class<?> agentClass, Object agentInstance) {
        com.embabel.agent.api.annotation.Agent agentAnnotation = agentClass
                .getAnnotation(com.embabel.agent.api.annotation.Agent.class);
        if (agentAnnotation == null) {
            logger.warn("Class {} has no @Agent annotation — skipping", agentClass.getName());
            return null;
        }

        // @LlmTool discovery — zero Spring AI dependency
        List<Tool> tools = Tool.safelyFromInstance(agentInstance);
        logger.debug("Discovered {} @LlmTool(s) on {}", tools.size(), agentClass.getSimpleName());

        List<Action> actions = createActions(agentClass, agentInstance, tools);
        Set<Condition> conditions = createConditions(agentClass, agentInstance);

        String name = agentAnnotation.name().isEmpty()
                ? agentClass.getSimpleName()
                : agentAnnotation.name();
        String provider = agentAnnotation.provider().isEmpty()
                ? agentClass.getPackage().getName()
                : agentAnnotation.provider();

        logger.debug("Building Agent '{}' with {} action(s) and {} condition(s)",
                name, actions.size(), conditions.size());

        // @JvmOverloads secondary constructor — goals left empty (MVP scope)
        return new Agent(name, provider, agentAnnotation.version(), agentAnnotation.description(),
                Collections.emptySet(), actions, conditions);
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
                        method, agentInstance, tools, Collections.emptyMap());
                actions.add(action);
                logger.debug("Registered @Action: {}.{}", agentClass.getSimpleName(), method.getName());
            } catch (Exception e) {
                logger.warn("Failed to create action from {}.{}: {}",
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
            logger.debug("Registered @Condition: {}.{}", agentClass.getSimpleName(), method.getName());
        }
        return conditions;
    }

    private boolean invokeConditionMethod(Method method, Object instance, Object context) {
        try {
            return Boolean.TRUE.equals(method.invoke(instance, context));
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.warn("@Condition {}.{} threw: {}",
                    instance.getClass().getSimpleName(), method.getName(), cause.getMessage());
            return false;
        } catch (IllegalAccessException e) {
            logger.warn("Cannot invoke @Condition {}.{}: {}",
                    instance.getClass().getSimpleName(), method.getName(), e.getMessage());
            return false;
        }
    }
}
