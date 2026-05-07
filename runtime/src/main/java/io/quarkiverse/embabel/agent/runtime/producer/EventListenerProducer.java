package io.quarkiverse.embabel.agent.runtime.producer;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import com.embabel.agent.api.event.AgenticEventListener;
import com.embabel.agent.spi.logging.ColorPalette;
import com.embabel.agent.spi.logging.LoggingAgenticEventListener;
import com.embabel.agent.spi.support.AgenticEventListenerToolsStats;

import io.quarkus.arc.DefaultBean;

/**
 * CDI producer for Embabel Agent event listener beans.
 * <p>
 * This producer creates event-related dependencies required by {@link com.embabel.agent.core.support.DefaultAgentPlatform},
 * following the pattern established by {@link io.quarkiverse.embabel.agent.runtime.provider.QuarkusModelProvider}.
 * <p>
 * The beans produced here include:
 * <ul>
 * <li>Default logging event listener</li>
 * <li>Aggregated event listener that broadcasts to all registered listeners</li>
 * <li>Tool usage statistics collector</li>
 * </ul>
 * <p>
 * <b>Event Listener Aggregation</b>:
 * The {@link #agenticEventListener(Instance)} method discovers all {@link AgenticEventListener} beans
 * via CDI and aggregates them into a single listener that broadcasts events to all registered listeners.
 * This allows users to provide custom event listeners that will automatically be included in the
 * event broadcasting chain.
 *
 * @see com.embabel.agent.core.support.DefaultAgentPlatform
 * @see com.embabel.agent.spi.config.spring.AgentPlatformConfiguration
 */
@ApplicationScoped
public class EventListenerProducer {

    @Inject
    ColorPalette colorPalette;

    /**
     * Produces a default {@link LoggingAgenticEventListener} for logging agent events.
     * Uses the injected {@link ColorPalette} for colored terminal output.
     * Can be overridden by user-provided beans.
     * <p>
     * Note: LoggingAgenticEventListener has Kotlin default parameters. From Java, we pass
     * null for url and welcomeMessage, null for logger (uses default), and our ColorPalette.
     *
     * @return the default logging event listener
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    public LoggingAgenticEventListener loggingAgenticEventListener() {
        // Call Kotlin constructor with default parameters: url=null, welcomeMessage=null, logger=null (default), colorPalette
        return new LoggingAgenticEventListener(null, null, null, colorPalette);
    }

    /**
     * Produces an aggregated {@link AgenticEventListener} that broadcasts events to all registered listeners.
     * <p>
     * This method discovers all {@link AgenticEventListener} beans via CDI (including the default
     * {@link LoggingAgenticEventListener} and any user-provided listeners) and creates a composite
     * listener that forwards events to all of them.
     * <p>
     * This follows the same pattern as {@link io.quarkiverse.embabel.agent.runtime.provider.QuarkusModelProvider}
     * for aggregating multiple service implementations.
     * <p>
     * Note: This bean is named "aggregatedEventListener" to distinguish it from the individual
     * listener implementations (LoggingAgenticEventListener, AgenticEventListenerToolsStats).
     *
     * @param listeners CDI instance containing all registered event listeners
     * @return an aggregated event listener that broadcasts to all listeners
     */
    @Produces
    @ApplicationScoped
    @Named("aggregatedEventListener")
    public AgenticEventListener agenticEventListener(
            Instance<AgenticEventListener> listeners) {
        List<AgenticEventListener> listenerList = new ArrayList<>();
        listeners.forEach(listenerList::add);
        // Access Kotlin companion object method via Companion.from()
        return AgenticEventListener.Companion.from(listenerList);
    }

    /**
     * Produces an {@link AgenticEventListenerToolsStats} for collecting tool usage statistics.
     * This listener tracks tool invocations and can be used for monitoring and debugging.
     *
     * @return the tool statistics collector
     */
    @Produces
    @ApplicationScoped
    public AgenticEventListenerToolsStats toolsStats() {
        return new AgenticEventListenerToolsStats();
    }
}