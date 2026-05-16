package io.quarkiverse.embabel.agent.deployment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import com.embabel.agent.spi.LlmService;
import com.embabel.common.ai.model.EmbeddingService;

import io.quarkiverse.embabel.agent.runtime.ActionMethodBuildInfo;
import io.quarkiverse.embabel.agent.runtime.AgentDeploymentRecorder;
import io.quarkiverse.embabel.agent.runtime.ConditionMethodBuildInfo;
import io.quarkiverse.embabel.agent.runtime.CostMethodBuildInfo;
import io.quarkiverse.embabel.agent.runtime.LlmServiceRecorder;
import io.quarkiverse.embabel.agent.runtime.embedding.QuarkusEmbeddingService;
import io.quarkiverse.embabel.agent.runtime.loop.QuarkusToolLoopFactory;
import io.quarkiverse.embabel.agent.runtime.provider.QuarkusModelProvider;
import io.quarkiverse.embabel.agent.runtime.service.QuarkusLlmService;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.RequestChatModelBeanBuildItem;
import io.quarkiverse.langchain4j.deployment.items.AutoCreateEmbeddingModelBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
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
    private static final DotName AGENT_ANNOTATION = DotName.createSimple("com.embabel.agent.api.annotation.Agent");
    private static final DotName ACTION_ANNOTATION = DotName.createSimple("com.embabel.agent.api.annotation.Action");
    private static final DotName ACHIEVES_GOAL_ANNOTATION = DotName
            .createSimple("com.embabel.agent.api.annotation.AchievesGoal");
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
     * and registers them as CDI beans. It also performs comprehensive metadata collection
     * using Jandex, including scanning the full class hierarchy (interfaces, superclasses)
     * for @Action, @Condition, and @Cost methods.
     *
     * @param combinedIndex the combined Jandex index of all application classes
     * @param additionalBeans producer for additional bean build items
     * @param agentMetadataProducer producer for comprehensive agent metadata
     * @return build item containing the list of discovered agent class names
     */
    @BuildStep
    AgentClassesBuildItem discoverAgents(
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<AgentMetadataBuildItem> agentMetadataProducer) {

        IndexView index = combinedIndex.getIndex();
        List<String> agentClassNames = new ArrayList<>();
        AgentMethodScanner scanner = new AgentMethodScanner(index);

        // Storage for comprehensive metadata
        Map<String, List<ActionMethodBuildInfo>> actionMethodsByAgent = new HashMap<>();
        Map<String, List<ConditionMethodBuildInfo>> conditionMethodsByAgent = new HashMap<>();
        Map<String, List<CostMethodBuildInfo>> costMethodsByAgent = new HashMap<>();

        // Find all classes annotated with @Agent
        for (AnnotationInstance agentAnnotation : index.getAnnotations(AGENT_ANNOTATION)) {
            ClassInfo agentClass = agentAnnotation.target().asClass();
            String className = agentClass.name().toString();

            // Register the agent class as a CDI bean
            // Note: @Agent has @Component meta-annotation which Quarkus interprets as @Singleton
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(className)
                    .setUnremovable()
                    .build());

            // Collect class name for runtime deployment
            agentClassNames.add(className);

            // Scan the full class hierarchy for annotated methods
            // This includes methods from interfaces and superclasses that getDeclaredMethods() would miss
            List<ActionMethodBuildInfo> actionMethods = scanner.scanActionMethods(agentClass);
            List<ConditionMethodBuildInfo> conditionMethods = scanner.scanConditionMethods(agentClass);
            List<CostMethodBuildInfo> costMethods = scanner.scanCostMethods(agentClass);

            if (!actionMethods.isEmpty()) {
                actionMethodsByAgent.put(className, actionMethods);
            }
            if (!conditionMethods.isEmpty()) {
                conditionMethodsByAgent.put(className, conditionMethods);
            }
            if (!costMethods.isEmpty()) {
                costMethodsByAgent.put(className, costMethods);
            }
        }

        // Produce comprehensive metadata build item for runtime use
        agentMetadataProducer.produce(new AgentMetadataBuildItem(
                actionMethodsByAgent,
                conditionMethodsByAgent,
                costMethodsByAgent));

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
     * <li>Create agent metadata using QuarkusAgentDeployer with build-time metadata</li>
     * <li>Build actions, conditions, and goals from pre-scanned metadata</li>
     * <li>Deploy the agent to the AgentPlatform</li>
     * </ol>
     *
     * @param recorder the recorder for runtime agent deployment
     * @param agentClasses the build item containing discovered agent class names
     * @param agentMetadata comprehensive metadata about all agent methods
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void deployAgents(
            AgentDeploymentRecorder recorder,
            AgentClassesBuildItem agentClasses,
            AgentMetadataBuildItem agentMetadata) {
        // Extract data from build item - recorders can't receive build items directly
        Map<String, List<ActionMethodBuildInfo>> actionMethodsByAgent = new HashMap<>();
        Map<String, List<ConditionMethodBuildInfo>> conditionMethodsByAgent = new HashMap<>();
        Map<String, List<CostMethodBuildInfo>> costMethodsByAgent = new HashMap<>();

        for (String className : agentClasses.getAgentClassNames()) {
            actionMethodsByAgent.put(className, agentMetadata.getActionMethods(className));
            conditionMethodsByAgent.put(className, agentMetadata.getConditionMethods(className));
            costMethodsByAgent.put(className, agentMetadata.getCostMethods(className));
        }

        recorder.deployAgents(
                agentClasses.getAgentClassNames(),
                actionMethodsByAgent,
                conditionMethodsByAgent,
                costMethodsByAgent);
    }

    /**
     * Request creation of the default ChatModel bean.
     * <p>
     * This build step tells quarkus-langchain4j to create a default ChatModel bean
     * even though our extension doesn't directly inject it. Instead, we wrap ChatModel
     * instances in QuarkusLlmService beans.
     * <p>
     * Without this, the langchain4j core extension won't create ChatModel beans
     * because it only creates them when there's an injection point or explicit request.
     *
     * @param requestChatModelProducer producer for requesting ChatModel bean creation
     */
    @BuildStep
    void requestChatModel(BuildProducer<RequestChatModelBeanBuildItem> requestChatModelProducer) {
        // Request the default ChatModel bean
        // This ensures that at least one ChatModel is created, which our extension
        // will then wrap in a QuarkusLlmService bean
        requestChatModelProducer.produce(new RequestChatModelBeanBuildItem(NamedConfigUtil.DEFAULT_NAME));
    }

    /**
     * Request creation of embedding models.
     * <p>
     * This build step produces an {@link AutoCreateEmbeddingModelBuildItem} to ensure
     * that quarkus-langchain4j creates EmbeddingModel beans when configured, which we
     * will then wrap in QuarkusEmbeddingService beans.
     * <p>
     * This is necessary because our extension doesn't directly inject EmbeddingModel
     * (we create synthetic beans that depend on it), so without this build item,
     * quarkus-langchain4j wouldn't know to create the EmbeddingModel beans.
     *
     * @param autoCreateProducer producer for requesting EmbeddingModel bean creation
     */
    @BuildStep
    void requestEmbeddingModels(BuildProducer<AutoCreateEmbeddingModelBuildItem> autoCreateProducer) {
        // Request that quarkus-langchain4j auto-create embedding models when configured
        autoCreateProducer.produce(new AutoCreateEmbeddingModelBuildItem());
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
                    .addType(LlmService.class) // Add raw interface type for non-parameterized injection
                    .scope(ApplicationScoped.class)
                    .setRuntimeInit()
                    .unremovable()
                    .createWith(recorder.createLlmService(configName, provider));

            // Declare ChatModel as an injection point dependency
            // This tells CDI that LlmService depends on ChatModel and ensures proper initialization order
            if (!NamedConfigUtil.isDefault(configName)) {
                // Named model - inject ChatModel with @ModelName qualifier
                configurator.addInjectionPoint(
                        ClassType.create(DotName.createSimple("dev.langchain4j.model.chat.ChatModel")),
                        AnnotationInstance.builder(ModelName.class)
                                .add("value", configName)
                                .build());
                // Add @ModelName qualifier to the LlmService bean itself
                configurator.addQualifier(
                        AnnotationInstance.builder(ModelName.class)
                                .add("value", configName)
                                .build());
            } else {
                // Default model - inject ChatModel without qualifier
                configurator.addInjectionPoint(
                        ClassType.create(DotName.createSimple("dev.langchain4j.model.chat.ChatModel")));
                configurator.defaultBean();
            }

            syntheticBeans.produce(configurator.done());
        }
    }

    /**
     * Registers EmbeddingService beans for each configured EmbeddingModel.
     * <p>
     * NOTE: This build step may not be called if quarkus-langchain4j doesn't produce
     * SelectedEmbeddingModelCandidateBuildItem. In that case, embedding models are
     * created as regular CDI beans and can be injected directly.
     * <p>
     * For each EmbeddingModel bean created by quarkus-langchain4j (identified by
     * {@link SelectedEmbeddingModelCandidateBuildItem}), this creates a corresponding
     * QuarkusEmbeddingService bean with the same qualifier.
     * <p>
     * Example configuration:
     *
     * <pre>
     * # Default embedding model (no qualifier)
     * quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
     * quarkus.langchain4j.openai.embedding-model.enabled=true
     * quarkus.langchain4j.openai.embedding-model.model-name=text-embedding-3-small
     *
     * # Named embedding model with @ModelName("fast") qualifier
     * quarkus.langchain4j.openai.fast.api-key=${OPENAI_API_KEY}
     * quarkus.langchain4j.openai.fast.embedding-model.enabled=true
     * quarkus.langchain4j.openai.fast.embedding-model.model-name=text-embedding-ada-002
     * </pre>
     *
     * @param recorder the runtime recorder for creating instances
     * @param selectedEmbeddingModels the list of configured EmbeddingModel providers
     * @param syntheticBeans producer for synthetic bean build items
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerEmbeddingServiceBeans(
            LlmServiceRecorder recorder,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbeddingModels,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {

        for (SelectedEmbeddingModelCandidateBuildItem selected : selectedEmbeddingModels) {
            String configName = selected.getConfigName();
            String provider = selected.getProvider();

            // Create corresponding EmbeddingService bean
            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(QuarkusEmbeddingService.class)
                    .addType(EmbeddingService.class)
                    .scope(ApplicationScoped.class)
                    .setRuntimeInit()
                    .unremovable()
                    .createWith(recorder.createEmbeddingService(configName, provider));

            // Declare EmbeddingModel as an injection point dependency
            // This tells CDI that EmbeddingService depends on EmbeddingModel and ensures proper initialization order
            if (!NamedConfigUtil.isDefault(configName)) {
                // Named model - inject EmbeddingModel with @ModelName qualifier
                configurator.addInjectionPoint(
                        ClassType.create(DotName.createSimple("dev.langchain4j.model.embedding.EmbeddingModel")),
                        AnnotationInstance.builder(ModelName.class)
                                .add("value", configName)
                                .build());
                // Add @ModelName qualifier to the EmbeddingService bean itself
                configurator.addQualifier(
                        AnnotationInstance.builder(ModelName.class)
                                .add("value", configName)
                                .build());
            } else {
                // Default model - inject EmbeddingModel without qualifier
                configurator.addInjectionPoint(
                        ClassType.create(DotName.createSimple("dev.langchain4j.model.embedding.EmbeddingModel")));
                configurator.defaultBean();
            }

            syntheticBeans.produce(configurator.done());
        }
    }

    /**
     * Registers extension beans for CDI discovery.
     * <p>
     * This build step registers the core extension beans and their producers:
     * <ul>
     * <li>{@link QuarkusToolLoopFactory} - Creates tool execution loop instances</li>
     * <li>{@link QuarkusModelProvider} - Discovers LlmService and EmbeddingService beans via CDI</li>
     * <li>CoreBeansProducer - Produces core Embabel beans</li>
     * <li>EventListenerProducer - Produces event listeners for agent lifecycle</li>
     * <li>ToolProducer - Produces tool-related beans including ToolGroupResolver</li>
     * <li>LlmOperationsProducer - Produces LlmOperations for agent execution</li>
     * <li>AgentPlatformProducer - Produces the AgentPlatform bean</li>
     * </ul>
     * <p>
     * All beans are marked as unremovable to ensure they are available at runtime,
     * even if not directly injected (they may be discovered via CDI Instance).
     * <p>
     * Note: LlmService beans are registered separately in {@link #registerLlmServiceBeans}
     * as synthetic beans, created dynamically for each ChatModel configured via quarkus-langchain4j.
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
    }
}
