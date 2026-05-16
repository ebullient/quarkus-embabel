package io.quarkiverse.embabel.agent.runtime;

import java.util.List;

/**
 * Build-time metadata about a @Condition method.
 * Discovered via Jandex during build, including methods inherited from interfaces/superclasses.
 */
public class ConditionMethodBuildInfo {

    private final String className;
    private final String methodName;
    private final List<ParameterBuildInfo> parameters;
    private final String conditionName;
    private final double cost;

    public ConditionMethodBuildInfo(
            String className,
            String methodName,
            List<ParameterBuildInfo> parameters,
            String conditionName,
            double cost) {
        this.className = className;
        this.methodName = methodName;
        this.parameters = parameters;
        this.conditionName = conditionName;
        this.cost = cost;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public List<ParameterBuildInfo> getParameters() {
        return parameters;
    }

    public String getConditionName() {
        return conditionName;
    }

    public double getCost() {
        return cost;
    }

    @Override
    public String toString() {
        return "@Condition " + className + "." + methodName + "() [" + conditionName + "]";
    }
}
