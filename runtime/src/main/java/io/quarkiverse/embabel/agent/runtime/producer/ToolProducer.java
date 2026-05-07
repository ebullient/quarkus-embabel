package io.quarkiverse.embabel.agent.runtime.producer;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.spi.ToolDecorator;
import com.embabel.agent.spi.ToolGroupResolver;
import com.embabel.agent.spi.support.DefaultToolDecorator;
import com.embabel.agent.spi.support.RegistryToolGroupResolver;

import io.micrometer.observation.ObservationRegistry;

/**
 * CDI producer for Embabel Agent tool-related beans.
 * <p>
 * This producer creates tool-related dependencies required by {@link com.embabel.agent.core.support.DefaultAgentPlatform},
 * following the pattern established by {@link io.quarkiverse.embabel.agent.runtime.provider.QuarkusModelProvider}.
 * <p>
 * The beans produced here include:
 * <ul>
 * <li>Tool group resolver that aggregates all registered tool groups</li>
 * <li>Tool decorator for adding observability and transformation to tools</li>
 * </ul>
 * <p>
 * <b>Tool Group Aggregation</b>:
 * The {@link #toolGroupResolver(Instance)} method discovers all {@link ToolGroup} beans
 * via CDI and aggregates them into a single resolver. This allows users to provide custom
 * tool groups that will automatically be included in the agent platform.
 * <p>
 * <b>Observability Integration</b>:
 * The {@link #toolDecorator(ToolGroupResolver, Instance)} method optionally integrates with
 * Micrometer's {@link ObservationRegistry} if available, enabling tool execution metrics
 * and tracing.
 *
 * @see com.embabel.agent.core.support.DefaultAgentPlatform
 * @see com.embabel.agent.spi.config.spring.AgentPlatformConfiguration
 */
@ApplicationScoped
public class ToolProducer {

    /**
     * Produces a {@link ToolGroupResolver} that aggregates all registered tool groups.
     * <p>
     * This method discovers all {@link ToolGroup} beans via CDI and creates a registry-based
     * resolver that can look up tools by name. This follows the same pattern as
     * {@link io.quarkiverse.embabel.agent.runtime.provider.QuarkusModelProvider} for
     * aggregating multiple service implementations.
     *
     * @param toolGroups CDI instance containing all registered tool groups
     * @return a tool group resolver containing all discovered tool groups
     */
    @Produces
    @ApplicationScoped
    public ToolGroupResolver toolGroupResolver(
            Instance<ToolGroup> toolGroups) {
        List<ToolGroup> allToolGroups = new ArrayList<>();
        toolGroups.forEach(allToolGroups::add);
        return new RegistryToolGroupResolver(
                "QuarkusToolGroupResolver",
                allToolGroups);
    }

    /**
     * Produces a {@link ToolDecorator} for decorating tools with observability and transformation.
     * <p>
     * This decorator wraps tool executions with:
     * <ul>
     * <li>Observability metrics (if {@link ObservationRegistry} is available)</li>
     * <li>Output transformation for consistent formatting</li>
     * </ul>
     * <p>
     * The observability integration is optional - if no {@link ObservationRegistry} bean is found,
     * a no-op registry is used instead.
     *
     * @param toolGroupResolver the tool group resolver for looking up tools
     * @param observationRegistry optional CDI instance of ObservationRegistry for metrics
     * @return a tool decorator with observability and transformation capabilities
     */
    @Produces
    @ApplicationScoped
    public ToolDecorator toolDecorator(
            ToolGroupResolver toolGroupResolver,
            Instance<ObservationRegistry> observationRegistry) {
        return new DefaultToolDecorator(
                toolGroupResolver,
                observationRegistry.isUnsatisfied()
                        ? ObservationRegistry.NOOP
                        : observationRegistry.get(),
                // StringTransformer is a Kotlin fun interface - create via lambda
                raw -> raw); // Identity transformer
    }
}