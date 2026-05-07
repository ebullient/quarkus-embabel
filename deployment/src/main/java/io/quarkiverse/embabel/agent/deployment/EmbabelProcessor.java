package io.quarkiverse.embabel.agent.deployment;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import com.embabel.agent.spi.LlmService;

import io.quarkiverse.embabel.agent.runtime.AgentDeploymentRecorder;
import io.quarkiverse.embabel.agent.runtime.LlmServiceRecorder;
import io.quarkiverse.embabel.agent.runtime.loop.QuarkusToolLoop;
import io.quarkiverse.embabel.agent.runtime.loop.QuarkusToolLoopFactory;
import io.quarkiverse.embabel.agent.runtime.message.MessageConverterImpl;
import io.quarkiverse.embabel.agent.runtime.provider.QuarkusModelProvider;
import io.quarkiverse.embabel.agent.runtime.service.QuarkusLlmService;
import io.quarkiverse.embabel.agent.runtime.tool.ToolSpecificationConverterImpl;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Quarkus build-time processor for the Embabel Agent extension.
 * This processor registers the extension feature and handles
 * build-time configuration and bean registration.
 */
public class EmbabelProcessor {

    private static final String FEATURE = "embabel-agent";
    private static final String LLM_SERVICE = "com.embabel.agent.spi.LlmService";
    private static final DotName AGENT_ANNOTATION = DotName.createSimple("com.embabel.agent.api.annotation.Agent");
    private static final DotName TOOL_GROUP_ANNOTATION = DotName.createSimple("com.embabel.agent.api.annotation.ToolGroup");

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
     * Discovers and registers classes annotated with @Agent as CDI beans.
     * <p>
     * This build step scans the application for classes with the @Agent annotation
     * and registers them as CDI beans so they can be discovered by the AgentPlatform
     * at runtime. The @Agent annotation itself defines the scope, so we don't override it.
     *
     * @param combinedIndex the combined Jandex index of all application classes
     * @param additionalBeans producer for additional bean build items
     * @return build item containing the list of discovered agent class names
     */
    @BuildStep
    AgentClassesBuildItem discoverAgents(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        IndexView index = combinedIndex.getIndex();
        List<String> agentClassNames = new ArrayList<>();

        // Find all classes annotated with @Agent
        for (AnnotationInstance agentAnnotation : index.getAnnotations(AGENT_ANNOTATION)) {
            ClassInfo agentClass = agentAnnotation.target().asClass();
            String className = agentClass.name().toString();

            // Register the agent class as a CDI bean
            // Don't set a default scope - @Agent annotation already defines the scope
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(className)
                    .setUnremovable()
                    .build());

            // Collect class name for runtime deployment
            agentClassNames.add(className);
        }

        return new AgentClassesBuildItem(agentClassNames);
    }

    /**
     * Discovers and registers classes annotated with @ToolGroup as CDI beans.
     * <p>
     * This build step scans the application for classes with the @ToolGroup annotation
     * and registers them as ApplicationScoped CDI beans so they can be discovered by the
     * ToolProducer's {@code Instance<ToolGroup>} injection.
     * <p>
     * This allows users to create custom ToolGroup implementations without needing to
     * manually add CDI scope annotations like {@code @ApplicationScoped}.
     *
     * @param combinedIndex the combined Jandex index of all application classes
     * @param additionalBeans producer for additional bean build items
     */
    @BuildStep
    void discoverToolGroups(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        IndexView index = combinedIndex.getIndex();

        // Find all classes annotated with @ToolGroup
        for (AnnotationInstance toolGroupAnnotation : index.getAnnotations(TOOL_GROUP_ANNOTATION)) {
            ClassInfo toolGroupClass = toolGroupAnnotation.target().asClass();
            String className = toolGroupClass.name().toString();

            // Register as ApplicationScoped CDI bean
            // @ToolGroup annotation doesn't define scope, so we set it explicitly
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(className)
                    .setDefaultScope(DotName.createSimple(ApplicationScoped.class.getName()))
                    .setUnremovable()
                    .build());
        }
    }

    /**
     * Deploys discovered agent beans to the AgentPlatform at runtime.
     * <p>
     * This build step uses a recorder to deploy all discovered agent beans
     * to the AgentPlatform during application startup. The recorder will:
     * <ol>
     * <li>Look up each agent bean from the CDI container</li>
     * <li>Create agent metadata using AgentMetadataReader</li>
     * <li>Deploy the agent to the AgentPlatform</li>
     * </ol>
     *
     * @param recorder the recorder for runtime agent deployment
     * @param agentClasses the build item containing discovered agent class names
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void deployAgents(
            AgentDeploymentRecorder recorder,
            AgentClassesBuildItem agentClasses) {
        recorder.deployAgents(agentClasses.getAgentClassNames());
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

    /**
     * Registers extension beans for CDI discovery.
     * <p>
     * This build step registers the core extension beans that are needed for the
     * Embabel Agent framework to function properly in Quarkus:
     * <ul>
     * <li>{@link QuarkusToolLoop} - Tool execution loop implementation</li>
     * <li>{@link QuarkusModelProvider} - Model provider for LLM and embedding service discovery</li>
     * <li>{@link MessageConverterImpl} - Message format converter between Embabel and LangChain4j</li>
     * <li>{@link ToolSpecificationConverterImpl} - Tool specification converter</li>
     * </ul>
     * <p>
     * All beans are marked as unremovable to ensure they are available at runtime,
     * even if not directly injected (they may be discovered via CDI Instance).
     * <p>
     * Note: LlmService beans are registered separately in {@link #registerLlmServiceBeans}
     * as synthetic beans, since they are created dynamically based on quarkus-langchain4j
     * ChatModel configuration.
     *
     * @param additionalBeans producer for additional bean build items
     */
    @BuildStep
    void registerExtensionBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        // Register tool loop factory
        // QuarkusToolLoopFactory creates QuarkusToolLoop instances per-request
        // Already has @ApplicationScoped but marked unremovable to prevent build-time removal
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(QuarkusToolLoopFactory.class));

        // Register model provider
        // QuarkusModelProvider discovers LlmService and EmbeddingService beans via CDI
        // Already has @ApplicationScoped but marked unremovable to prevent build-time removal
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(QuarkusModelProvider.class));

        // Register CDI producer classes for AgentPlatform dependencies
        // These producers create all the beans needed by DefaultAgentPlatform
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
                io.quarkiverse.embabel.agent.runtime.producer.CoreBeansProducer.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
                io.quarkiverse.embabel.agent.runtime.producer.EventListenerProducer.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
                io.quarkiverse.embabel.agent.runtime.producer.ToolProducer.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
                io.quarkiverse.embabel.agent.runtime.producer.LlmOperationsProducer.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(
                io.quarkiverse.embabel.agent.runtime.producer.AgentPlatformProducer.class));

        // Note: AgentMetadataReader cannot be used in Quarkus due to Spring AI classloader issues
        // It tries to scan for Spring AI @Tool annotations which fails in Quarkus
        // This is a known limitation - agents must use Embabel @LlmTool annotations only
    }
}
