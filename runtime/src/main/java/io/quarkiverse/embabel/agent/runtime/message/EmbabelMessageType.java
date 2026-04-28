package io.quarkiverse.embabel.agent.runtime.message;

/**
 * Enum representing the different types of messages in the Embabel agent system.
 * This enum is used to identify and categorize messages during conversion between
 * Embabel and LangChain4j message formats.
 */
public enum EmbabelMessageType {
    /**
     * Message from a user/human.
     * Maps to {@link com.embabel.chat.UserMessage} in Embabel
     * and {@link dev.langchain4j.data.message.UserMessage} in LangChain4j.
     */
    USER,

    /**
     * Message from the AI assistant without tool calls.
     * Maps to {@link com.embabel.chat.AssistantMessage} in Embabel
     * and {@link dev.langchain4j.data.message.AiMessage} in LangChain4j.
     */
    ASSISTANT,

    /**
     * System message providing context or instructions.
     * Maps to {@link com.embabel.chat.SystemMessage} in Embabel
     * and {@link dev.langchain4j.data.message.SystemMessage} in LangChain4j.
     */
    SYSTEM,

    /**
     * Message containing the result of a tool execution.
     * Maps to {@link com.embabel.chat.ToolResultMessage} in Embabel
     * and {@link dev.langchain4j.data.message.ToolExecutionResultMessage} in LangChain4j.
     */
    TOOL_RESULT,

    /**
     * Assistant message that includes tool execution requests.
     * Maps to {@link com.embabel.chat.AssistantMessageWithToolCalls} in Embabel
     * and {@link dev.langchain4j.data.message.AiMessage} (with tool execution requests) in LangChain4j.
     */
    ASSISTANT_WITH_TOOLS
}