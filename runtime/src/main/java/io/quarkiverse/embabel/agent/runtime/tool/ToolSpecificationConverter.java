package io.quarkiverse.embabel.agent.runtime.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

/**
 * Converts Embabel Tool definitions to LangChain4j ToolSpecification format.
 * <p>
 * This interface bridges the gap between the framework-agnostic Embabel Tool API
 * and LangChain4j's tool representation, enabling Embabel tools to be used with
 * LangChain4j-based LLM providers in Quarkus applications.
 */
public interface ToolSpecificationConverter {

    /**
     * Converts an Embabel Tool to a LangChain4j ToolSpecification.
     *
     * @param embabelTool the Embabel tool to convert
     * @return the equivalent LangChain4j ToolSpecification
     */
    ToolSpecification toLangChain4j(com.embabel.agent.api.tool.Tool embabelTool);

    /**
     * Converts an Embabel Tool InputSchema to LangChain4j JsonObjectSchema.
     *
     * @param schema the Embabel input schema to convert
     * @return the equivalent LangChain4j JsonObjectSchema for tool parameters
     */
    JsonObjectSchema convertInputSchema(com.embabel.agent.api.tool.Tool.InputSchema schema);
}