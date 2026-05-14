package io.quarkiverse.embabel.agent.runtime.producer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.api.common.Asyncer;
import com.embabel.agent.api.event.AgenticEventListener;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessRepository;
import com.embabel.agent.core.expression.LogicalExpressionParser;
import com.embabel.agent.core.internal.LlmOperations;
import com.embabel.agent.core.support.DefaultAgentPlatform;
import com.embabel.agent.spi.AgentProcessIdGenerator;
import com.embabel.agent.spi.BlackboardProvider;
import com.embabel.agent.spi.ContextRepository;
import com.embabel.agent.spi.OperationScheduler;
import com.embabel.agent.spi.ToolGroupResolver;
import com.embabel.agent.spi.config.spring.AgentPlatformProperties;
import com.embabel.common.textio.template.TemplateRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CDI producer for the {@link AgentPlatform} bean.
 * <p>
 * This is the final integration point that brings together all the dependencies required by
 * {@link DefaultAgentPlatform}. It follows the pattern established by
 * {@link io.quarkiverse.embabel.agent.runtime.provider.QuarkusModelProvider} of using CDI
 * to gather dependencies and then delegating to Embabel's implementation.
 * <p>
 * <b>Architecture</b>:
 *
 * <pre>
 * CDI Producers → Dependencies → DefaultAgentPlatform
 *      ↓              ↓                    ↓
 *   Gather      Instantiate           Delegate
 *   Beans       Platform              All Logic
 * </pre>
 * <p>
 * <b>Key Decision</b>: We do NOT replace {@link DefaultAgentPlatform}. It's already
 * framework-agnostic except for the {@code @Service} annotation. Replacing it would require
 * reimplementing 200+ lines of critical logic including agent deployment, process lifecycle,
 * event broadcasting, blackboard management, and tool aggregation.
 * <p>
 * <b>Configuration</b>:
 * The platform can be configured via application.properties:
 * <ul>
 * <li>{@code embabel.agent.platform.name} - Platform name (default: "quarkus-agent-platform")</li>
 * <li>{@code embabel.agent.platform.description} - Platform description</li>
 * <li>{@code embabel.agent.platform.process-type} - Process type: SIMPLE or CONCURRENT</li>
 * </ul>
 *
 * @see DefaultAgentPlatform
 * @see com.embabel.agent.spi.config.spring.AgentPlatformConfiguration
 */
@ApplicationScoped
public class AgentPlatformProducer {

    @ConfigProperty(name = "embabel.agent.platform.name", defaultValue = "quarkus-agent-platform")
    String platformName;

    @ConfigProperty(name = "embabel.agent.platform.description", defaultValue = "Quarkus Agent Platform")
    String platformDescription;

    @ConfigProperty(name = "embabel.agent.platform.process-type", defaultValue = "SIMPLE")
    AgentPlatformProperties.ProcessType processType;

    @Inject
    LlmOperations llmOperations;

    @Inject
    ToolGroupResolver toolGroupResolver;

    @Inject
    @Named("aggregatedEventListener")
    AgenticEventListener eventListener;

    @Inject
    AgentProcessIdGenerator agentProcessIdGenerator;

    @Inject
    ContextRepository contextRepository;

    @Inject
    AgentProcessRepository agentProcessRepository;

    @Inject
    OperationScheduler operationScheduler;

    @Inject
    BlackboardProvider blackboardProvider;

    @Inject
    Asyncer asyncer;

    @Inject
    @Named("embabelJacksonObjectMapper")
    ObjectMapper objectMapper;

    @Inject
    OutputChannel outputChannel;

    @Inject
    TemplateRenderer templateRenderer;

    @Inject
    Instance<LogicalExpressionParser> customLogicalExpressionParser;

    /**
     * Produces the {@link AgentPlatform} bean by instantiating {@link DefaultAgentPlatform}
     * with all required dependencies.
     * <p>
     * This method gathers all the dependencies that have been produced by other producer classes
     * ({@link CoreBeansProducer}, {@link EventListenerProducer}, {@link ToolProducer}) and
     * passes them to {@link DefaultAgentPlatform}'s constructor.
     * <p>
     * <b>Note on ApplicationContext</b>: The last parameter is {@code null} because Quarkus
     * doesn't use Spring's ApplicationContext. This is acceptable because DefaultAgentPlatform
     * makes ApplicationContext optional (nullable parameter).
     *
     * @return the configured agent platform instance
     */
    @Produces
    @ApplicationScoped
    public AgentPlatform agentPlatform() {
        return new DefaultAgentPlatform(
                platformName,
                platformDescription,
                processType,
                llmOperations,
                toolGroupResolver,
                eventListener,
                agentProcessIdGenerator,
                contextRepository,
                agentProcessRepository,
                operationScheduler,
                blackboardProvider,
                asyncer,
                objectMapper,
                outputChannel,
                templateRenderer,
                customLogicalExpressionParser.isUnsatisfied()
                        ? null
                        : customLogicalExpressionParser.get(),
                null // ApplicationContext - not needed in Quarkus
        );
    }
}
