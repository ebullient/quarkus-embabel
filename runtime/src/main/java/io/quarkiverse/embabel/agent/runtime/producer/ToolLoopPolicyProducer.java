package io.quarkiverse.embabel.agent.runtime.producer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.slf4j.LoggerFactory;

import com.embabel.agent.api.tool.config.ToolLoopConfiguration;
import com.embabel.agent.spi.loop.AutoCorrectionPolicy;
import com.embabel.agent.spi.loop.EmptyResponsePolicy;
import com.embabel.agent.spi.loop.ExitOnEmptyPolicy;
import com.embabel.agent.spi.loop.RetryWithFeedbackPolicy;
import com.embabel.agent.spi.loop.ToolNotFoundPolicy;

import io.quarkus.arc.DefaultBean;

/**
 * CDI producer for ToolLoop policy beans.
 * <p>
 * This producer replicates the functionality of Embabel's
 * {@code com.embabel.agent.spi.config.spring.ToolLoopFactoryConfiguration}
 * for Quarkus/CDI environments.
 * <p>
 * The Spring {@code @Configuration} class cannot be reliably used because:
 * <ul>
 * <li>It uses constructor injection with {@code @EnableConfigurationProperties} which has limited support in
 * quarkus-spring-di</li>
 * <li>It's in an external Kotlin library with different initialization semantics</li>
 * </ul>
 * <p>
 * Instead, we produce the same beans here using CDI {@code @DefaultBean} (equivalent to
 * {@code @ConditionalOnMissingBean}) to allow user overrides.
 *
 * @see com.embabel.agent.spi.config.spring.ToolLoopFactoryConfiguration
 * @see ToolLoopConfiguration
 */
@ApplicationScoped
public class ToolLoopPolicyProducer {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ToolLoopPolicyProducer.class);

    /**
     * Produces {@link ToolNotFoundPolicy} configured from {@link ToolLoopConfiguration}.
     * <p>
     * Creates an {@link AutoCorrectionPolicy} with:
     * <ul>
     * <li>{@code maxRetries} from {@code embabel.agent.platform.toolloop.tool-not-found.max-retries}</li>
     * <li>{@code minFuzzyLength} from {@code embabel.agent.platform.toolloop.tool-not-found.min-fuzzy-length}</li>
     * </ul>
     * <p>
     * Users can override by providing their own {@link ToolNotFoundPolicy} bean without {@code @DefaultBean}.
     *
     * @param config the tool loop configuration
     * @return configured tool-not-found policy
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    public ToolNotFoundPolicy toolNotFoundPolicy(ToolLoopConfiguration config) {
        return new AutoCorrectionPolicy(
                config.getToolNotFound().getMaxRetries(),
                config.getToolNotFound().getMinFuzzyLength());
    }

    /**
     * Produces {@link EmptyResponsePolicy} configured from {@link ToolLoopConfiguration}.
     * <p>
     * Creates either:
     * <ul>
     * <li>{@link RetryWithFeedbackPolicy} if {@code maxRetries > 0} - retries with nudge message</li>
     * <li>{@link ExitOnEmptyPolicy} if {@code maxRetries == 0} - exits immediately</li>
     * </ul>
     * <p>
     * Configuration from:
     * <ul>
     * <li>{@code embabel.agent.platform.toolloop.empty-response.max-retries}</li>
     * <li>{@code embabel.agent.platform.toolloop.empty-response.nudge-message}</li>
     * </ul>
     * <p>
     * Users can override by providing their own {@link EmptyResponsePolicy} bean without {@code @DefaultBean}.
     *
     * @param config the tool loop configuration
     * @return configured empty-response policy
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    public EmptyResponsePolicy emptyResponsePolicy(ToolLoopConfiguration config) {
        if (config.getEmptyResponse().getMaxRetries() > 0) {
            return new RetryWithFeedbackPolicy(
                    config.getEmptyResponse().getMaxRetries(),
                    config.getEmptyResponse().getNudgeMessage());
        } else {
            return ExitOnEmptyPolicy.INSTANCE;
        }
    }
}
