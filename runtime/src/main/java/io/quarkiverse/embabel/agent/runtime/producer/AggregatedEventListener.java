package io.quarkiverse.embabel.agent.runtime.producer;

import com.embabel.agent.api.event.AgenticEventListener;

/**
 * Marker interface for the aggregated event listener.
 * <p>
 * Enables type-safe filtering during CDI bean resolution. Unlike Spring's {@code List<T>}
 * injection which excludes the bean being produced, CDI's {@code Instance<T>} includes
 * a proxy to it, requiring explicit filtering to prevent circular references.
 *
 * @see EventListenerProducer#agenticEventListener(jakarta.enterprise.inject.Instance)
 */
public interface AggregatedEventListener extends AgenticEventListener {
    // Marker interface - no additional methods needed
}