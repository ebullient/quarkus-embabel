package io.quarkiverse.embabel.agent.runtime.producer

import com.embabel.agent.api.common.Ai
import com.embabel.agent.api.common.AiBuilder
import com.embabel.agent.api.common.ExecutingOperationContext
import com.embabel.agent.api.dsl.agent
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.ProcessOptions
import com.embabel.agent.core.Verbosity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Produces

/**
 * CDI producer for Ai-related beans that can be injected into components.
 *
 * This class provides the same functionality as Spring Boot's
 * `InfrastructureInjectionConfiguration`, allowing components to inject:
 * - [Ai] - Direct access to AI operations
 * - [ExecutingOperationContext] - Full operation context
 * - [AiBuilder] - Builder for creating Ai instances with custom options
 *
 * All beans are dependent-scoped (equivalent to Spring's prototype scope),
 * meaning a new instance is created for each injection point.
 *
 * @see com.embabel.agent.spi.config.spring.InfrastructureInjectionConfiguration
 */
@ApplicationScoped
open class AiBeansProducer {

    /**
     * Produces an [ExecutingOperationContext] for injection into components.
     *
     * This is a dependent-scoped bean (equivalent to Spring's prototype scope),
     * creating a new instance for each injection point. The context is created
     * with a placeholder agent process for the calling class.
     *
     * Mirrors Spring Boot's `executingOperationContextFactory`.
     *
     * @param agentPlatform the agent platform
     * @return a new ExecutingOperationContext instance
     */
    @Produces
    @Dependent
    fun executingOperationContext(agentPlatform: AgentPlatform): ExecutingOperationContext {
        return createExecutingOperationContext(agentPlatform, ProcessOptions())
    }

    /**
     * Produces an [Ai] instance for injection into components.
     *
     * This is a dependent-scoped bean (equivalent to Spring's prototype scope),
     * creating a new instance for each injection point. The Ai instance is obtained
     * from an ExecutingOperationContext.
     *
     * Mirrors Spring Boot's `aiFactory`.
     *
     * @param agentPlatform the agent platform
     * @return a new Ai instance
     */
    @Produces
    @Dependent
    fun ai(agentPlatform: AgentPlatform): Ai {
        return createExecutingOperationContext(agentPlatform, ProcessOptions()).ai()
    }

    /**
     * Produces an [AiBuilder] for injection into components.
     *
     * This is a dependent-scoped bean (equivalent to Spring's prototype scope),
     * creating a new instance for each injection point. The builder allows
     * components to configure and create Ai instances with custom options.
     *
     * Mirrors Spring Boot's `aiBuilderFactory`.
     *
     * @param agentPlatform the agent platform
     * @return a new AiBuilder instance
     */
    @Produces
    @Dependent
    fun aiBuilder(agentPlatform: AgentPlatform): AiBuilder {
        return QuarkusAiBuilder(agentPlatform, ProcessOptions(verbosity = Verbosity(debug = true)))
    }

    /**
     * Creates an ExecutingOperationContext for the calling class.
     *
     * This method creates a placeholder agent process with a name derived from
     * the calling class, allowing components to use Ai functionality without
     * being part of a full agent execution.
     *
     * @param agentPlatform the agent platform
     * @param processOptions the process options
     * @return a new ExecutingOperationContext
     */
    private fun createExecutingOperationContext(
        agentPlatform: AgentPlatform,
        processOptions: ProcessOptions,
    ): ExecutingOperationContext {
        val callingClassName = findFirstUserClass()
        val agentForIdOnly = agent(
            name = callingClassName,
            description = "Empty agent for operation context injection into $callingClassName",
        ) {
            // No actions, just a placeholder
        }
        return ExecutingOperationContext(
            name = callingClassName,
            agentProcess = agentPlatform.createAgentProcess(
                agentForIdOnly,
                processOptions = processOptions,
                bindings = emptyMap(),
            ),
        )
    }

    /**
     * Finds the first user class in the call stack.
     *
     * This method walks the stack trace to find the first class in the
     * com.embabel.agent package that is not an inner class or constructor.
     *
     * @return the simple name of the calling class, or "Unknown" if not found
     */
    private fun findFirstUserClass(): String {
        val stackTrace = Thread.currentThread().stackTrace

        return stackTrace
            .firstOrNull { element ->
                element.className.startsWith("com.embabel.agent") &&
                        !element.className.contains("$") && // Avoid inner classes
                        !element.methodName.contains("<init>") // Avoid constructor calls
            }
            ?.className
            ?.substringAfterLast(".")
            ?: "Unknown"
    }

    /**
     * Implementation of AiBuilder for Quarkus.
     *
     * This class provides a builder pattern for creating Ai instances with
     * custom process options and verbosity settings.
     */
    private data class QuarkusAiBuilder(
        val agentPlatform: AgentPlatform,
        val processOptions: ProcessOptions,
    ) : AiBuilder {

        override fun withProcessOptions(options: ProcessOptions): AiBuilder =
            copy(processOptions = options)

        override val showPrompts: Boolean
            get() = processOptions.verbosity.showPrompts

        override val showLlmResponses: Boolean
            get() = processOptions.verbosity.showLlmResponses

        override fun ai(): Ai {
            val callingClassName = findFirstUserClass()
            val agentForIdOnly = agent(
                name = callingClassName,
                description = "Empty agent for operation context injection into $callingClassName",
            ) {
                // No actions, just a placeholder
            }
            return ExecutingOperationContext(
                name = callingClassName,
                agentProcess = agentPlatform.createAgentProcess(
                    agentForIdOnly,
                    processOptions = processOptions,
                    bindings = emptyMap(),
                ),
            ).ai()
        }

        override fun withShowPrompts(show: Boolean): AiBuilder =
            copy(processOptions = processOptions.copy(verbosity = processOptions.verbosity.copy(showPrompts = show)))

        override fun withShowLlmResponses(show: Boolean): AiBuilder =
            copy(processOptions = processOptions.copy(verbosity = processOptions.verbosity.copy(showLlmResponses = show)))

        private fun findFirstUserClass(): String {
            val stackTrace = Thread.currentThread().stackTrace
            return stackTrace
                .firstOrNull { element ->
                    element.className.startsWith("com.embabel.agent") &&
                            !element.className.contains("$") &&
                            !element.methodName.contains("<init>")
                }
                ?.className
                ?.substringAfterLast(".")
                ?: "Unknown"
        }
    }
}