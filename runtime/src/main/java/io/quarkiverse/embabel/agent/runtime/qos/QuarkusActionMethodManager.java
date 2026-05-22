package io.quarkiverse.embabel.agent.runtime.qos;

import java.util.Arrays;
import java.util.List;

import com.embabel.agent.api.annotation.support.ActionMethodArgumentResolver;
import com.embabel.agent.api.annotation.support.ActionQosProvider;
import com.embabel.agent.api.annotation.support.AiArgumentResolver;
import com.embabel.agent.api.annotation.support.BlackboardArgumentResolver;
import com.embabel.agent.api.annotation.support.DefaultActionMethodManager;
import com.embabel.agent.api.annotation.support.MethodDefinedOperationNameGenerator;
import com.embabel.agent.api.annotation.support.OperationContextArgumentResolver;
import com.embabel.agent.api.annotation.support.ProcessContextArgumentResolver;

/**
 * Quarkus-specific wrapper for {@link DefaultActionMethodManager}.
 * <p>
 * This class exists to work around Kotlin-Java interop issues with default parameters.
 * Kotlin's default parameters don't generate all constructor overloads for Java unless
 * annotated with {@code @JvmOverloads}, which the upstream class doesn't have.
 * <p>
 * This wrapper provides a Java-friendly constructor that accepts only the parameters
 * we need to customize (nameGenerator and actionQosProvider) while building the
 * argument resolvers list internally.
 */
public class QuarkusActionMethodManager extends DefaultActionMethodManager {

    /**
     * Constructor accepting custom name generator and QoS provider.
     * <p>
     * Builds the argument resolvers list to match the behavior of
     * {@code DefaultActionMethodManager.buildArgumentResolvers(null)}.
     *
     * @param nameGenerator the operation name generator
     * @param actionQosProvider the Action QoS provider
     */
    public QuarkusActionMethodManager(
            MethodDefinedOperationNameGenerator nameGenerator,
            ActionQosProvider actionQosProvider) {
        super(
                nameGenerator,
                actionQosProvider,
                null, // contextProvider - not used in Quarkus
                buildArgumentResolvers());
    }

    /**
     * Build the list of argument resolvers.
     * <p>
     * This matches the behavior of {@code DefaultActionMethodManager.buildArgumentResolvers(null)}:
     * <ul>
     * <li>ProcessContextArgumentResolver</li>
     * <li>OperationContextArgumentResolver</li>
     * <li>AiArgumentResolver</li>
     * <li>BlackboardArgumentResolver (fallback)</li>
     * </ul>
     * <p>
     * Note: ProvidedArgumentResolver is not included since contextProvider is null.
     *
     * @return the list of argument resolvers
     */
    private static List<ActionMethodArgumentResolver> buildArgumentResolvers() {
        return Arrays.asList(
                new ProcessContextArgumentResolver(),
                new OperationContextArgumentResolver(),
                new AiArgumentResolver(),
                new BlackboardArgumentResolver());
    }
}