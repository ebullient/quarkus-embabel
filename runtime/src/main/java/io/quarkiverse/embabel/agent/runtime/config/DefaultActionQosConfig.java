package io.quarkiverse.embabel.agent.runtime.config;

/**
 * Wrapper for the default Action QoS configuration.
 * <p>
 * This represents the fallback retry properties for {@code @Action} and {@code @Agent} overrides,
 * bound from properties with prefix {@code embabel.agent.platform.action-qos.default}.
 * <p>
 * These values are merged with {@link com.embabel.agent.core.ActionQos} defaults.
 *
 * @see com.embabel.agent.spi.config.spring.AgentPlatformProperties.ActionQosProperties
 */
public class DefaultActionQosConfig {

    /**
     * The default action QoS configuration.
     */
    private ActionQosConfig defaultConfig;

    /**
     * Default constructor for CDI and manual binding.
     */
    public DefaultActionQosConfig() {
        this.defaultConfig = new ActionQosConfig();
    }

    /**
     * Constructor with default config.
     *
     * @param defaultConfig the default action QoS configuration
     */
    public DefaultActionQosConfig(ActionQosConfig defaultConfig) {
        this.defaultConfig = defaultConfig != null ? defaultConfig : new ActionQosConfig();
    }

    public ActionQosConfig getDefaultConfig() {
        return defaultConfig;
    }

    public void setDefaultConfig(ActionQosConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    @Override
    public String toString() {
        return "DefaultActionQosConfig{" +
                "defaultConfig=" + defaultConfig +
                '}';
    }
}