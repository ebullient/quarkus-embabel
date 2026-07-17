package io.quarkiverse.embabel.agent.runtime.qos;

import com.embabel.agent.spi.config.spring.AgentPlatformProperties.ActionQosProperties.ActionProperties;

import io.quarkiverse.embabel.agent.runtime.config.NamedActionQosConfig;

/**
 * Quarkus-specific implementation of Action QoS property lookup.
 * <p>
 * Resolves named Action QoS configurations from {@link NamedActionQosConfig},
 * which is populated from properties matching:
 * {@code embabel.agent.platform.action-qos.{name}.{field}}
 * <p>
 * <b>Normalization:</b> Retry expressions are normalized to lookup keys:
 * <ul>
 * <li>{@code "${fast-retry}"} → {@code "fast-retry"}</li>
 * <li>{@code "fast-retry"} → {@code "fast-retry"}</li>
 * <li>{@code "agents.agent.method"} → {@code "agents.agent.method"}</li>
 * </ul>
 *
 * @see com.embabel.agent.api.annotation.support.ActionQosPropertyProvider
 * @see com.embabel.agent.api.annotation.support.DefaultActionQosProvider
 * @see io.quarkiverse.embabel.agent.runtime.producer.ActionQosProducer
 */
public class QuarkusActionQosPropertyProvider {

    private final NamedActionQosConfig namedConfig;

    public QuarkusActionQosPropertyProvider(NamedActionQosConfig namedConfig) {
        this.namedConfig = namedConfig;
    }

    /**
     * Get bound Action QoS properties for the given retry expression.
     *
     * @param expr the retry expression from {@code @Agent} or {@code @Action} annotation
     * @return the bound ActionProperties, or null if expression is blank or name not found
     */
    public ActionProperties getBound(String expr) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        String normalizedName = normalizeExpression(expr);
        return namedConfig.get(normalizedName);
    }

    String normalizeExpression(String expr) {
        String trimmed = expr.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return trimmed.substring(2, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
