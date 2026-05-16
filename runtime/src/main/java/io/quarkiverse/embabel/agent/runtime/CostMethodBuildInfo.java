package io.quarkiverse.embabel.agent.runtime;

import java.util.List;

/**
 * Build-time metadata about a @Cost method.
 * Discovered via Jandex during build, including methods inherited from interfaces/superclasses.
 */
public class CostMethodBuildInfo {

    private final String className;
    private final String methodName;
    private final List<ParameterBuildInfo> parameters;
    private final String costName;

    public CostMethodBuildInfo(
            String className,
            String methodName,
            List<ParameterBuildInfo> parameters,
            String costName) {
        this.className = className;
        this.methodName = methodName;
        this.parameters = parameters;
        this.costName = costName;
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

    public String getCostName() {
        return costName;
    }

    @Override
    public String toString() {
        return "@Cost " + className + "." + methodName + "() [" + costName + "]";
    }
}
