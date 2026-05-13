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
     * Discovers all {@link AgenticEventListener} beans (including {@link LoggingAgenticEventListener}
     * and user-provided listeners) and creates a composite listener. Filters out {@link AggregatedEventListener}
     * to prevent circular references (CDI includes a proxy to the bean being produced, unlike Spring).
     *
     * @param allListeners CDI instance containing all registered event listeners
     * @return an aggregated event listener that broadcasts to all individual listeners
     */
    @Produces
    @ApplicationScoped
    @Named("aggregatedEventListener")
    public AggregatedEventListener agenticEventListener(
            Instance<AgenticEventListener> allListeners) {
        List<AgenticEventListener> listenerList = new ArrayList<>();

        // Filter out AggregatedEventListener to prevent circular reference
        allListeners.stream()
                .filter(listener -> !(listener instanceof AggregatedEventListener))
                .forEach(listenerList::add);

        // Create multicast listener and return as AggregatedEventListener
        AgenticEventListener multicast = AgenticEventListener.Companion.from(listenerList);

        // Wrap multicast listener as AggregatedEventListener to enable type-safe filtering
        return new AggregatedEventListener() {
            @Override
            public void onPlatformEvent(com.embabel.agent.api.event.AgentPlatformEvent event) {
                multicast.onPlatformEvent(event);
            }

            @Override
            public void onProcessEvent(com.embabel.agent.api.event.AgentProcessEvent event) {
                multicast.onProcessEvent(event);
            }
        };
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