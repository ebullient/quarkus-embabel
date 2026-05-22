package io.quarkiverse.embabel.agent.runtime.config;

/**
 * Configuration for a single action's Quality of Service (QoS) settings.
 * <p>
 * This DTO mirrors the structure of
 * {@link com.embabel.agent.spi.config.spring.AgentPlatformProperties.ActionQosProperties.ActionProperties}
 * but is designed for manual SmallRye Config binding in Quarkus.
 * <p>
 * Null values mean "use defaults" (either the configured defaults or {@link com.embabel.agent.core.ActionQos}).
 * <p>
 * Property structure: {@code embabel.agent.platform.action-qos.{name}.{field}}
 * where {@code {name}} can be simple (e.g., "fast-retry") or hierarchical (e.g., "agents.agent.method").
 *
 * @see com.embabel.agent.spi.config.spring.AgentPlatformProperties.ActionQosProperties.ActionProperties
 */
public class ActionQosConfig {

    /**
     * Maximum number of retry attempts for this action.
     * Null means use the default value.
     */
    private Integer maxAttempts;

    /**
     * Initial backoff delay in milliseconds before the first retry.
     * Null means use the default value.
     */
    private Long backoffMillis;

    /**
     * Multiplier applied to backoff delay after each retry.
     * Null means use the default value.
     */
    private Double backoffMultiplier;

    /**
     * Maximum backoff interval in milliseconds (caps exponential growth).
     * Null means use the default value.
     */
    private Long backoffMaxInterval;

    /**
     * Whether this action is idempotent (safe to retry without side effects).
     * Null means use the default value.
     */
    private Boolean idempotent;

    /**
     * Default constructor for CDI and manual binding.
     */
    public ActionQosConfig() {
    }

    /**
     * Constructor with all fields for testing and manual construction.
     *
     * @param maxAttempts maximum retry attempts
     * @param backoffMillis initial backoff delay in milliseconds
     * @param backoffMultiplier backoff multiplier
     * @param backoffMaxInterval maximum backoff interval in milliseconds
     * @param idempotent whether action is idempotent
     */
    public ActionQosConfig(Integer maxAttempts, Long backoffMillis, Double backoffMultiplier,
            Long backoffMaxInterval, Boolean idempotent) {
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
        this.backoffMultiplier = backoffMultiplier;
        this.backoffMaxInterval = backoffMaxInterval;
        this.idempotent = idempotent;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Long getBackoffMillis() {
        return backoffMillis;
    }

    public void setBackoffMillis(Long backoffMillis) {
        this.backoffMillis = backoffMillis;
    }

    public Double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(Double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public Long getBackoffMaxInterval() {
        return backoffMaxInterval;
    }

    public void setBackoffMaxInterval(Long backoffMaxInterval) {
        this.backoffMaxInterval = backoffMaxInterval;
    }

    public Boolean getIdempotent() {
        return idempotent;
    }

    public void setIdempotent(Boolean idempotent) {
        this.idempotent = idempotent;
    }

    @Override
    public String toString() {
        return "ActionQosConfig{" +
                "maxAttempts=" + maxAttempts +
                ", backoffMillis=" + backoffMillis +
                ", backoffMultiplier=" + backoffMultiplier +
                ", backoffMaxInterval=" + backoffMaxInterval +
                ", idempotent=" + idempotent +
                '}';
    }
}