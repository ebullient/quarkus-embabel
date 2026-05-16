package io.quarkiverse.embabel.agent.runtime;

import java.util.List;

/**
 * Build-time metadata about a method parameter.
 * Captured during Jandex scanning to avoid runtime reflection lookups.
 */
public class ParameterBuildInfo {

    private final String name;
    private final String type;
    private final List<String> annotations;

    public ParameterBuildInfo(String name, String type, List<String> annotations) {
        this.name = name;
        this.type = type;
        this.annotations = annotations;
    }

    /**
     * Parameter name (may be synthetic like arg0, arg1 if -parameters not enabled).
     */
    public String getName() {
        return name;
    }

    /**
     * Fully qualified parameter type name.
     */
    public String getType() {
        return type;
    }

    /**
     * List of annotation class names present on this parameter.
     */
    public List<String> getAnnotations() {
        return annotations;
    }

    @Override
    public String toString() {
        return type + " " + name + (annotations.isEmpty() ? "" : " " + annotations);
    }
}
