package io.quarkiverse.embabel.agent.deployment;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import com.embabel.agent.spi.LlmService;

import io.quarkiverse.embabel.agent.runtime.LlmServiceRecorder;
import io.quarkiverse.embabel.agent.runtime.service.QuarkusLlmService;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Quarkus build-time processor for the Embabel Agent extension.
 * This processor registers the extension feature and handles
 * build-time configuration and bean registration.
 */
public class EmbabelProcessor {

    private static final String FEATURE = "embabel-agent";
    private static final String LLM_SERVICE = "com.embabel.agent.spi.LlmService";

    /**
     * Register the embabel-agent feature with Quarkus.
     * This will appear in the startup logs and extension listings.
     *
     * @return the feature build item
     */
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Registers LlmService beans for each configured ChatModel.
     * <p>
     * For each ChatModel bean created by quarkus-langchain4j (identified by
     * {@link SelectedChatModelProviderBuildItem}), this creates a corresponding
     * QuarkusLlmService bean with the same qualifier.
     * <p>
     * Example configuration:
     *
     * <pre>
     * # Default model (no qualifier)
     * quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
     * quarkus.langchain4j.openai.chat-model.model-name=gpt-4o
     *
     * # Named model with @ModelName("fast") qualifier
     * quarkus.langchain4j.openai.fast.api-key=${OPENAI_API_KEY}
     * quarkus.langchain4j.openai.fast.chat-model.model-name=gpt-4o-mini
     * </pre>
     *
     * @param recorder the runtime recorder for creating instances
     * @param selectedChatModels the list of configured ChatModel providers
     * @param syntheticBeans producer for synthetic bean build items
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerLlmServiceBeans(
            LlmServiceRecorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChatModels,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {

        // For each ChatModel bean created by quarkus-langchain4j
        for (SelectedChatModelProviderBuildItem selected : selectedChatModels) {
            String configName = selected.getConfigName();
            String provider = selected.getProvider();

            // Create corresponding LlmService bean
            // Must use ParameterizedType to properly represent LlmService<QuarkusLlmService>
            // This enables injection via the LlmService<?> interface
            ParameterizedType llmServiceType = ParameterizedType.create(
                    DotName.createSimple(LlmService.class.getName()),
                    new Type[] { ClassType.create(DotName.createSimple(QuarkusLlmService.class.getName())) },
                    null);

            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(QuarkusLlmService.class)
                    .addType(llmServiceType)
                    .scope(ApplicationScoped.class)
                    .setRuntimeInit()
                    .unremovable()
                    .createWith(recorder.createLlmService(configName, provider));

            // Add @ModelName qualifier for named models
            if (!NamedConfigUtil.isDefault(configName)) {
                configurator.addQualifier(
                        AnnotationInstance.builder(ModelName.class)
                                .add("value", configName)
                                .build());
            } else {
                configurator.defaultBean();
            }

            syntheticBeans.produce(configurator.done());
        }
    }
}
