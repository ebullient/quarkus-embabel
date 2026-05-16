package io.quarkiverse.embabel.agent.deployment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;

import io.quarkiverse.embabel.agent.runtime.ActionMethodBuildInfo;
import io.quarkiverse.embabel.agent.runtime.ConditionMethodBuildInfo;
import io.quarkiverse.embabel.agent.runtime.CostMethodBuildInfo;
import io.quarkiverse.embabel.agent.runtime.ParameterBuildInfo;

/**
 * Scans agent classes and their entire hierarchy (interfaces, superclasses) for annotated methods.
 * This solves the runtime limitation where getDeclaredMethods() doesn't see inherited methods.
 */
class AgentMethodScanner {

    private static final DotName ACTION_ANNOTATION = DotName.createSimple("com.embabel.agent.api.annotation.Action");
    private static final DotName CONDITION_ANNOTATION = DotName.createSimple("com.embabel.agent.api.annotation.Condition");
    private static final DotName COST_ANNOTATION = DotName.createSimple("com.embabel.agent.api.annotation.Cost");
    private static final DotName ACHIEVES_GOAL_ANNOTATION = DotName
            .createSimple("com.embabel.agent.api.annotation.AchievesGoal");

    private final IndexView index;

    AgentMethodScanner(IndexView index) {
        this.index = index;
    }

    /**
     * Scan an agent class and its full hierarchy for @Action methods.
     */
    List<ActionMethodBuildInfo> scanActionMethods(ClassInfo agentClass) {
        List<ActionMethodBuildInfo> actions = new ArrayList<>();
        Set<String> processedSignatures = new HashSet<>();

        scanActionMethodsRecursive(agentClass, actions, processedSignatures);

        return actions;
    }

    /**
     * Scan an agent class and its full hierarchy for @Condition methods.
     */
    List<ConditionMethodBuildInfo> scanConditionMethods(ClassInfo agentClass) {
        List<ConditionMethodBuildInfo> conditions = new ArrayList<>();
        Set<String> processedSignatures = new HashSet<>();

        scanConditionMethodsRecursive(agentClass, conditions, processedSignatures);

        return conditions;
    }

    /**
     * Scan an agent class and its full hierarchy for @Cost methods.
     */
    List<CostMethodBuildInfo> scanCostMethods(ClassInfo agentClass) {
        List<CostMethodBuildInfo> costs = new ArrayList<>();
        Set<String> processedSignatures = new HashSet<>();

        scanCostMethodsRecursive(agentClass, costs, processedSignatures);

        return costs;
    }

    private void scanActionMethodsRecursive(
            ClassInfo classInfo,
            List<ActionMethodBuildInfo> actions,
            Set<String> processedSignatures) {

        if (classInfo == null) {
            return;
        }

        String className = classInfo.name().toString();

        // Scan methods in this class
        for (MethodInfo method : classInfo.methods()) {
            if (!method.hasAnnotation(ACTION_ANNOTATION)) {
                continue;
            }

            // Avoid processing overridden methods multiple times
            String signature = methodSignature(method);
            if (processedSignatures.contains(signature)) {
                continue;
            }
            processedSignatures.add(signature);

            AnnotationInstance actionAnn = method.annotation(ACTION_ANNOTATION);
            AnnotationInstance achievesGoalAnn = method.annotation(ACHIEVES_GOAL_ANNOTATION);

            // Extract @Action annotation values
            String outputBinding = getAnnotationValue(actionAnn, "outputBinding", "");
            String costMethodName = getAnnotationValue(actionAnn, "cost", "");

            // Extract trigger type
            Type triggerType = actionAnn.value("trigger") != null
                    ? actionAnn.value("trigger").asClass()
                    : null;
            String triggerTypeName = (triggerType != null && !triggerType.name().toString().equals("kotlin.Unit"))
                    ? triggerType.name().toString()
                    : null;

            // Extract @AchievesGoal if present
            boolean achievesGoal = achievesGoalAnn != null;
            String goalDescription = achievesGoal
                    ? getAnnotationValue(achievesGoalAnn, "description", "Goal achieved by " + method.name())
                    : "";
            double goalValue = achievesGoal
                    ? getAnnotationValue(achievesGoalAnn, "value", 1.0)
                    : 1.0;

            // Validate non-void return for @AchievesGoal
            if (achievesGoal && method.returnType().kind() == Type.Kind.VOID) {
                throw new IllegalStateException(
                        String.format("@AchievesGoal on void method %s.%s - must return a value",
                                className, method.name()));
            }

            actions.add(new ActionMethodBuildInfo(
                    className,
                    method.name(),
                    method.returnType().name().toString(),
                    extractParameters(method),
                    outputBinding,
                    costMethodName.isEmpty() ? null : costMethodName,
                    triggerTypeName,
                    achievesGoal,
                    goalDescription,
                    goalValue));
        }

        // Recursively scan superclass
        if (classInfo.superName() != null && !classInfo.superName().toString().equals("java.lang.Object")) {
            ClassInfo superClass = index.getClassByName(classInfo.superName());
            if (superClass != null) {
                scanActionMethodsRecursive(superClass, actions, processedSignatures);
            }
        }

        // Recursively scan interfaces
        for (DotName interfaceName : classInfo.interfaceNames()) {
            ClassInfo interfaceClass = index.getClassByName(interfaceName);
            if (interfaceClass != null) {
                scanActionMethodsRecursive(interfaceClass, actions, processedSignatures);
            }
        }
    }

    private void scanConditionMethodsRecursive(
            ClassInfo classInfo,
            List<ConditionMethodBuildInfo> conditions,
            Set<String> processedSignatures) {

        if (classInfo == null) {
            return;
        }

        String className = classInfo.name().toString();

        for (MethodInfo method : classInfo.methods()) {
            if (!method.hasAnnotation(CONDITION_ANNOTATION)) {
                continue;
            }

            String signature = methodSignature(method);
            if (processedSignatures.contains(signature)) {
                continue;
            }
            processedSignatures.add(signature);

            AnnotationInstance conditionAnn = method.annotation(CONDITION_ANNOTATION);
            String conditionName = getAnnotationValue(conditionAnn, "name", "");
            double cost = getAnnotationValue(conditionAnn, "cost", 0.0);

            conditions.add(new ConditionMethodBuildInfo(
                    className,
                    method.name(),
                    extractParameters(method),
                    conditionName.isEmpty() ? className + "." + method.name() : conditionName,
                    cost));
        }

        // Recursively scan hierarchy
        if (classInfo.superName() != null && !classInfo.superName().toString().equals("java.lang.Object")) {
            ClassInfo superClass = index.getClassByName(classInfo.superName());
            if (superClass != null) {
                scanConditionMethodsRecursive(superClass, conditions, processedSignatures);
            }
        }

        for (DotName interfaceName : classInfo.interfaceNames()) {
            ClassInfo interfaceClass = index.getClassByName(interfaceName);
            if (interfaceClass != null) {
                scanConditionMethodsRecursive(interfaceClass, conditions, processedSignatures);
            }
        }
    }

    private void scanCostMethodsRecursive(
            ClassInfo classInfo,
            List<CostMethodBuildInfo> costs,
            Set<String> processedSignatures) {

        if (classInfo == null) {
            return;
        }

        String className = classInfo.name().toString();

        for (MethodInfo method : classInfo.methods()) {
            if (!method.hasAnnotation(COST_ANNOTATION)) {
                continue;
            }

            String signature = methodSignature(method);
            if (processedSignatures.contains(signature)) {
                continue;
            }
            processedSignatures.add(signature);

            AnnotationInstance costAnn = method.annotation(COST_ANNOTATION);
            String costName = getAnnotationValue(costAnn, "name", "");

            costs.add(new CostMethodBuildInfo(
                    className,
                    method.name(),
                    extractParameters(method),
                    costName.isEmpty() ? className + "." + method.name() : costName));
        }

        // Recursively scan hierarchy
        if (classInfo.superName() != null && !classInfo.superName().toString().equals("java.lang.Object")) {
            ClassInfo superClass = index.getClassByName(classInfo.superName());
            if (superClass != null) {
                scanCostMethodsRecursive(superClass, costs, processedSignatures);
            }
        }

        for (DotName interfaceName : classInfo.interfaceNames()) {
            ClassInfo interfaceClass = index.getClassByName(interfaceName);
            if (interfaceClass != null) {
                scanCostMethodsRecursive(interfaceClass, costs, processedSignatures);
            }
        }
    }

    /**
     * Extract parameter metadata from a method.
     */
    private List<ParameterBuildInfo> extractParameters(MethodInfo method) {
        List<ParameterBuildInfo> params = new ArrayList<>();

        for (int i = 0; i < method.parametersCount(); i++) {
            MethodParameterInfo param = method.parameters().get(i);
            String paramName = param.name() != null ? param.name() : "arg" + i;
            String paramType = method.parameterType(i).name().toString();

            // Extract annotations on this parameter
            List<String> annotations = new ArrayList<>();
            for (AnnotationInstance ann : method.annotations()) {
                if (ann.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.METHOD_PARAMETER) {
                    if (ann.target().asMethodParameter().position() == i) {
                        annotations.add(ann.name().toString());
                    }
                }
            }

            params.add(new ParameterBuildInfo(paramName, paramType, annotations));
        }

        return params;
    }

    /**
     * Create a unique signature for method deduplication across hierarchy.
     */
    private String methodSignature(MethodInfo method) {
        StringBuilder sig = new StringBuilder(method.name()).append("(");
        for (int i = 0; i < method.parametersCount(); i++) {
            if (i > 0)
                sig.append(",");
            sig.append(method.parameterType(i).name().toString());
        }
        sig.append(")");
        return sig.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> T getAnnotationValue(AnnotationInstance annotation, String name, T defaultValue) {
        AnnotationValue value = annotation.value(name);
        if (value == null) {
            return defaultValue;
        }

        if (defaultValue instanceof String) {
            return (T) value.asString();
        } else if (defaultValue instanceof Double) {
            return (T) Double.valueOf(value.asDouble());
        } else if (defaultValue instanceof Integer) {
            return (T) Integer.valueOf(value.asInt());
        } else if (defaultValue instanceof Boolean) {
            return (T) Boolean.valueOf(value.asBoolean());
        }

        return defaultValue;
    }
}
