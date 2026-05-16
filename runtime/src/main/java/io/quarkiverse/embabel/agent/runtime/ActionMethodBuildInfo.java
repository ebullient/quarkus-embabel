package io.quarkiverse.embabel.agent.runtime;

import java.util.List;

/**
 * Build-time metadata about an @Action method.
 * Discovered via Jandex during build, including methods inherited from interfaces/superclasses.
 */
public class ActionMethodBuildInfo {

    private final String className;
    private final String methodName;
    private final String returnType;
    private final List<ParameterBuildInfo> parameters;
    private final String outputBinding;
    private final String costMethodName;
    private final String triggerType;
    private final boolean achievesGoal;
    private final String goalDescription;
    private final double goalValue;

    public ActionMethodBuildInfo(
            String className,
            String methodName,
            String returnType,
            List<ParameterBuildInfo> parameters,
            String outputBinding,
            String costMethodName,
            String triggerType,
            boolean achievesGoal,
            String goalDescription,
            double goalValue) {
        this.className = className;
        this.methodName = methodName;
        this.returnType = returnType;
        this.parameters = parameters;
        this.outputBinding = outputBinding;
        this.costMethodName = costMethodName;
        this.triggerType = triggerType;
        this.achievesGoal = achievesGoal;
        this.goalDescription = goalDescription;
        this.goalValue = goalValue;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<ParameterBuildInfo> getParameters() {
        return parameters;
    }

    public String getOutputBinding() {
        return outputBinding;
    }

    public String getCostMethodName() {
        return costMethodName;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public boolean isAchievesGoal() {
        return achievesGoal;
    }

    public String getGoalDescription() {
        return goalDescription;
    }

    public double getGoalValue() {
        return goalValue;
    }

    /**
     * Fully qualified action name used at runtime (matches Action.getName()).
     */
    public String getFullyQualifiedName() {
        return className + "." + methodName;
    }

    @Override
    public String toString() {
        return "@Action " + className + "." + methodName + "() -> " + returnType +
                (achievesGoal ? " [@AchievesGoal]" : "");
    }
}
