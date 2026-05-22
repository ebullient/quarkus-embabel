package io.quarkiverse.embabel.agent.runtime.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Container for named Action QoS configurations.
 * <p>
 * This holds a map of named QoS configurations bound from properties with pattern:
 * {@code embabel.agent.platform.action-qos.{name}.{field}}
 * <p>
 * Names can be simple (e.g., "fast-retry") or hierarchical (e.g., "agents.agent.method").
 * <p>
 * Example properties:
 *
 * <pre>
 * embabel.agent.platform.action-qos.fast-retry.max-attempts=5
 * embabel.agent.platform.action-qos.fast-retry.backoff-millis=100
 * embabel.agent.platform.action-qos.agents.agent.method.max-attempts=3
 * </pre>
 *
 * @see ActionQosConfig
 */
public class NamedActionQosConfig {

    /**
     * Map of named Action QoS configurations.
     * Key is the configuration name (e.g., "fast-retry" or "agents.agent.method").
     * Value is the corresponding ActionQosConfig.
     */
    private Map<String, ActionQosConfig> namedConfigs;

    /**
     * Default constructor for CDI and manual binding.
     */
    public NamedActionQosConfig() {
        this.namedConfigs = new HashMap<>();
    }

    /**
     * Constructor with named configs map.
     *
     * @param namedConfigs map of named configurations
     */
    public NamedActionQosConfig(Map<String, ActionQosConfig> namedConfigs) {
        this.namedConfigs = namedConfigs != null ? new HashMap<>(namedConfigs) : new HashMap<>();
    }

    /**
     * Get the map of named configurations.
     *
     * @return unmodifiable view of named configurations
     */
    public Map<String, ActionQosConfig> getNamedConfigs() {
        return Collections.unmodifiableMap(namedConfigs);
    }

    /**
     * Set the map of named configurations.
     *
     * @param namedConfigs map of named configurations
     */
    public void setNamedConfigs(Map<String, ActionQosConfig> namedConfigs) {
        this.namedConfigs = namedConfigs != null ? new HashMap<>(namedConfigs) : new HashMap<>();
    }

    /**
     * Get a specific named configuration.
     *
     * @param name the configuration name
     * @return the configuration, or null if not found
     */
    public ActionQosConfig get(String name) {
        return namedConfigs.get(name);
    }

    /**
     * Add or update a named configuration.
     *
     * @param name the configuration name
     * @param config the configuration
     */
    public void put(String name, ActionQosConfig config) {
        namedConfigs.put(name, config);
    }

    /**
     * Check if a named configuration exists.
     *
     * @param name the configuration name
     * @return true if the configuration exists
     */
    public boolean contains(String name) {
        return namedConfigs.containsKey(name);
    }

    /**
     * Get the number of named configurations.
     *
     * @return the size of the named configs map
     */
    public int size() {
        return namedConfigs.size();
    }

    @Override
    public String toString() {
        return "NamedActionQosConfig{" +
                "namedConfigs=" + namedConfigs +
                '}';
    }
}