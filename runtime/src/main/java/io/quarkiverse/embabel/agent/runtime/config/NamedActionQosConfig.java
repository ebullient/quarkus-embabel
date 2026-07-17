package io.quarkiverse.embabel.agent.runtime.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.embabel.agent.spi.config.spring.AgentPlatformProperties.ActionQosProperties.ActionProperties;

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
 * @see ActionProperties
 */
public class NamedActionQosConfig {

    private Map<String, ActionProperties> namedConfigs;

    public NamedActionQosConfig() {
        this.namedConfigs = new HashMap<>();
    }

    public NamedActionQosConfig(
            Map<String, ActionProperties> namedConfigs) {
        this.namedConfigs = namedConfigs != null ? new HashMap<>(namedConfigs) : new HashMap<>();
    }

    public Map<String, ActionProperties> getNamedConfigs() {
        return Collections.unmodifiableMap(namedConfigs);
    }

    public void setNamedConfigs(
            Map<String, ActionProperties> namedConfigs) {
        this.namedConfigs = namedConfigs != null ? new HashMap<>(namedConfigs) : new HashMap<>();
    }

    public ActionProperties get(String name) {
        return namedConfigs.get(name);
    }

    public void put(String name, ActionProperties config) {
        namedConfigs.put(name, config);
    }

    public boolean contains(String name) {
        return namedConfigs.containsKey(name);
    }

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
