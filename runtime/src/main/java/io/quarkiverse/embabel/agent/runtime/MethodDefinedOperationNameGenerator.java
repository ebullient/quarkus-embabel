package io.quarkiverse.embabel.agent.runtime;

import io.quarkus.arc.ClientProxy;

/**
 * Generates qualified names for operations defined in methods.
 * <p>
 * Matches Spring Boot's {@code FromClassAndMethodMethodDefinedOperationNameGenerator}
 * to ensure consistent goal naming between Spring Boot and Quarkus deployments.
 * <p>
 * Generates names in the format: {@code "fully.qualified.ClassName.methodName"}
 * <p>
 * Implements the upstream {@link com.embabel.agent.api.annotation.support.MethodDefinedOperationNameGenerator}
 * interface to be compatible with {@link com.embabel.agent.api.annotation.support.DefaultActionMethodManager}.
 */
class MethodDefinedOperationNameGenerator
        implements com.embabel.agent.api.annotation.support.MethodDefinedOperationNameGenerator {

    /**
     * Generate a qualified name to avoid name clashes.
     * <p>
     * <b>Proxy Handling</b>: This implementation uses Arc's {@link ClientProxy} interface
     * to unwrap CDI proxies and get the actual bean class. This is more robust than
     * Spring Boot's {@code ClassUtils.getUserClass()} for Quarkus environments.
     * <p>
     * For Arc client proxies, we call {@code arc_bean().getBeanClass()} to get the
     * actual class. For subclass proxies or non-proxied beans, we use the instance's class.
     * <p>
     * Matches Spring Boot's name generation format to ensure consistent goal naming.
     *
     * @param instance The instance of the class we are reading
     * @param name The name of the method or property for which we should generate a name
     * @return Fully qualified name in format "com.example.ClassName.methodName"
     */
    @Override
    public String generateName(Object instance, String name) {
        // Unwrap Arc CDI client proxies to get the actual bean class
        Class<?> targetClass;
        if (instance instanceof ClientProxy) {
            // Arc client proxy - get the actual bean class
            targetClass = ((ClientProxy) instance).arc_bean().getBeanClass();
        } else {
            // Not a client proxy - use the instance's class directly
            // (could be a subclass proxy or actual bean instance)
            targetClass = instance.getClass();
        }

        return targetClass.getName() + "." + stripDollarSign(name);
    }

    /**
     * Strip dollar signs from method names (can appear in synthetic methods).
     */
    private String stripDollarSign(String input) {
        int dollarIndex = input.indexOf('$');
        return dollarIndex >= 0 ? input.substring(0, dollarIndex) : input;
    }
}
