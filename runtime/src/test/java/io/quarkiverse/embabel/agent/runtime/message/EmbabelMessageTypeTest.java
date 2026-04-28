package io.quarkiverse.embabel.agent.runtime.message;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EmbabelMessageType} enum.
 * Tests only verify the enum's existence and basic usage, not Java's built-in enum functionality.
 */
class EmbabelMessageTypeTest {

    @Test
    void testAllMessageTypesExist() {
        // Verify all expected enum values exist and count is correct
        EmbabelMessageType[] values = EmbabelMessageType.values();
        assertEquals(5, values.length, "Should have exactly 5 message types");

        // Verify each expected value can be accessed
        assertNotNull(EmbabelMessageType.USER);
        assertNotNull(EmbabelMessageType.ASSISTANT);
        assertNotNull(EmbabelMessageType.SYSTEM);
        assertNotNull(EmbabelMessageType.TOOL_RESULT);
        assertNotNull(EmbabelMessageType.ASSISTANT_WITH_TOOLS);
    }

    @Test
    void testEnumCanBeUsedInSwitch() {
        // Verify enum can be used in switch statements (compile-time check)
        for (EmbabelMessageType type : EmbabelMessageType.values()) {
            String result = switch (type) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                case SYSTEM -> "system";
                case TOOL_RESULT -> "tool_result";
                case ASSISTANT_WITH_TOOLS -> "assistant_with_tools";
            };
            assertNotNull(result, "Switch should handle all enum values");
        }
    }
}