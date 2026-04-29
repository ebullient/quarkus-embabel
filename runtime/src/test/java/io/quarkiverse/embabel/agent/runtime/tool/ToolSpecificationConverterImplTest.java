package io.quarkiverse.embabel.agent.runtime.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.embabel.agent.api.tool.Tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;

/**
 * Unit tests for {@link ToolSpecificationConverterImpl}.
 * <p>
 * Tests verify:
 * <ul>
 * <li>Simple tool conversion with basic parameter types</li>
 * <li>Complex parameter types (nested objects, arrays, enums)</li>
 * <li>Required vs optional parameters</li>
 * <li>Mixed parameter types</li>
 * <li>Error handling</li>
 * </ul>
 */
class ToolSpecificationConverterImplTest {

    private ToolSpecificationConverterImpl converter;

    @BeforeEach
    void setUp() {
        converter = new ToolSpecificationConverterImpl();
    }

    // Step 10 Tests: Basic Tool Conversion

    @Test
    @DisplayName("Should convert simple tool with single string parameter")
    void shouldConvertSimpleToolWithStringParameter() {
        // Given
        Tool.Definition definition = Tool.Definition.create(
                "greet_user",
                "Greets a user by name",
                Tool.InputSchema.of(
                        Tool.Parameter.string("name", "The user's name", true)));
        Tool tool = createMockTool(definition);

        // When
        ToolSpecification spec = converter.toLangChain4j(tool);

        // Then
        assertThat(spec.name()).isEqualTo("greet_user");
        assertThat(spec.description()).isEqualTo("Greets a user by name");
        assertThat(spec.parameters()).isNotNull();
        assertThat(spec.parameters().properties()).containsKey("name");
        assertThat(spec.parameters().properties().get("name")).isInstanceOf(JsonStringSchema.class);
        assertThat(spec.parameters().required()).contains("name");
    }

    @Test
    @DisplayName("Should convert tool with multiple parameters of different types")
    void shouldConvertToolWithMultipleParameters() {
        // Given
        Tool.Definition definition = Tool.Definition.create(
                "calculate",
                "Performs a calculation",
                Tool.InputSchema.of(
                        Tool.Parameter.integer("operand1", "First number", true),
                        Tool.Parameter.integer("operand2", "Second number", true),
                        Tool.Parameter.string("operation", "Operation to perform", true)));
        Tool tool = createMockTool(definition);

        // When
        ToolSpecification spec = converter.toLangChain4j(tool);

        // Then
        assertThat(spec.name()).isEqualTo("calculate");
        assertThat(spec.parameters().properties()).hasSize(3);
        assertThat(spec.parameters().properties().get("operand1")).isInstanceOf(JsonIntegerSchema.class);
        assertThat(spec.parameters().properties().get("operand2")).isInstanceOf(JsonIntegerSchema.class);
        assertThat(spec.parameters().properties().get("operation")).isInstanceOf(JsonStringSchema.class);
        assertThat(spec.parameters().required()).containsExactlyInAnyOrder("operand1", "operand2", "operation");
    }

    @Test
    @DisplayName("Should convert tool with required and optional parameters")
    void shouldConvertToolWithRequiredAndOptionalParameters() {
        // Given
        Tool.Definition definition = Tool.Definition.create(
                "search",
                "Searches for information",
                Tool.InputSchema.of(
                        Tool.Parameter.string("query", "Search query", true),
                        Tool.Parameter.integer("limit", "Maximum results", false)));
        Tool tool = createMockTool(definition);

        // When
        ToolSpecification spec = converter.toLangChain4j(tool);

        // Then
        assertThat(spec.parameters().properties()).hasSize(2);
        assertThat(spec.parameters().required()).containsExactly("query");
        assertThat(spec.parameters().required()).doesNotContain("limit");
    }

    @Test
    @DisplayName("Should convert tool with all basic parameter types")
    void shouldConvertToolWithAllBasicTypes() {
        // Given
        Tool.Definition definition = Tool.Definition.create(
                "test_types",
                "Tests all basic types",
                Tool.InputSchema.of(
                        Tool.Parameter.string("str_param", "String parameter"),
                        Tool.Parameter.integer("int_param", "Integer parameter"),
                        new Tool.Parameter("num_param", Tool.ParameterType.NUMBER, "Number parameter"),
                        new Tool.Parameter("bool_param", Tool.ParameterType.BOOLEAN, "Boolean parameter")));
        Tool tool = createMockTool(definition);

        // When
        ToolSpecification spec = converter.toLangChain4j(tool);

        // Then
        assertThat(spec.parameters().properties()).hasSize(4);
        assertThat(spec.parameters().properties().get("str_param")).isInstanceOf(JsonStringSchema.class);
        assertThat(spec.parameters().properties().get("int_param")).isInstanceOf(JsonIntegerSchema.class);
        assertThat(spec.parameters().properties().get("num_param")).isInstanceOf(JsonNumberSchema.class);
        assertThat(spec.parameters().properties().get("bool_param")).isInstanceOf(JsonBooleanSchema.class);
    }

    // Step 11 Tests: Complex Parameter Types

    @Test
    @DisplayName("Should convert tool with enum parameter")
    void shouldConvertToolWithEnumParameter() {
        // Given
        List<String> enumValues = Arrays.asList("red", "green", "blue");
        Tool.Definition definition = Tool.Definition.create(
                "set_color",
                "Sets a color",
                Tool.InputSchema.of(
                        new Tool.Parameter("color", Tool.ParameterType.STRING, "Color to set", true, enumValues, null,
                                null)));
        Tool tool = createMockTool(definition);

        // When
        ToolSpecification spec = converter.toLangChain4j(tool);

        // Then
        assertThat(spec.parameters().properties().get("color")).isInstanceOf(JsonEnumSchema.class);
        JsonEnumSchema enumSchema = (JsonEnumSchema) spec.parameters().properties().get("color");
        assertThat(enumSchema.enumValues()).containsExactlyInAnyOrder("red", "green", "blue");
    }

    @Test
    @DisplayName("Should convert tool with array parameter")
    void shouldConvertToolWithArrayParameter() {
        // Given
        Tool.Definition definition = Tool.Definition.create(
                "process_items",
                "Processes a list of items",
                Tool.InputSchema.of(
                        new Tool.Parameter("items", Tool.ParameterType.ARRAY, "List of items", true, null, null,
                                Tool.ParameterType.STRING)));
        Tool tool = createMockTool(definition);

        // When
        ToolSpecification spec = converter.toLangChain4j(tool);

        // Then
        assertThat(spec.parameters().properties().get("items")).isInstanceOf(JsonArraySchema.class);
        JsonArraySchema arraySchema = (JsonArraySchema) spec.parameters().properties().get("items");
        assertThat(arraySchema.items()).isInstanceOf(JsonStringSchema.class);
    }

    @Test
    @DisplayName("Should convert tool with nested object parameter")
    void shouldConvertToolWithNestedObjectParameter() {
        // Given
        List<Tool.Parameter> addressProperties = Arrays.asList(
                Tool.Parameter.string("street", "Street address"),
                Tool.Parameter.string("city", "City"),
                Tool.Parameter.string("zipcode", "ZIP code"));

        Tool.Definition definition = Tool.Definition.create(
                "save_contact",
                "Saves contact information",
                Tool.InputSchema.of(
                        Tool.Parameter.string("name", "Contact name", true),
                        new Tool.Parameter("address", Tool.ParameterType.OBJECT, "Address", true, null,
                                addressProperties, null)));
        Tool tool = createMockTool(definition);

        // When
        ToolSpecification spec = converter.toLangChain4j(tool);

        // Then
        assertThat(spec.parameters().properties().get("address")).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema objectSchema = (JsonObjectSchema) spec.parameters().properties().get("address");
        assertThat(objectSchema.properties()).hasSize(3);
        assertThat(objectSchema.properties()).containsKeys("street", "city", "zipcode");
        assertThat(objectSchema.properties().get("street")).isInstanceOf(JsonStringSchema.class);
    }

    @Test
    @DisplayName("Should convert tool with complex mixed parameter types")
    void shouldConvertToolWithComplexMixedTypes() {
        // Given
        List<Tool.Parameter> configProperties = Arrays.asList(
                Tool.Parameter.string("key", "Configuration key"),
                Tool.Parameter.string("value", "Configuration value"));

        Tool.Definition definition = Tool.Definition.create(
                "complex_tool",
                "A tool with complex parameters",
                Tool.InputSchema.of(
                        Tool.Parameter.string("name", "Name", true),
                        Tool.Parameter.integer("count", "Count", false),
                        new Tool.Parameter("tags", Tool.ParameterType.ARRAY, "Tags", false, null, null,
                                Tool.ParameterType.STRING),
                        new Tool.Parameter("config", Tool.ParameterType.OBJECT, "Configuration", false, null,
                                configProperties, null),
                        new Tool.Parameter("priority", Tool.ParameterType.STRING, "Priority level", true,
                                Arrays.asList("low", "medium", "high"), null, null)));
        Tool tool = createMockTool(definition);

        // When
        ToolSpecification spec = converter.toLangChain4j(tool);

        // Then
        assertThat(spec.parameters().properties()).hasSize(5);
        assertThat(spec.parameters().properties().get("name")).isInstanceOf(JsonStringSchema.class);
        assertThat(spec.parameters().properties().get("count")).isInstanceOf(JsonIntegerSchema.class);
        assertThat(spec.parameters().properties().get("tags")).isInstanceOf(JsonArraySchema.class);
        assertThat(spec.parameters().properties().get("config")).isInstanceOf(JsonObjectSchema.class);
        assertThat(spec.parameters().properties().get("priority")).isInstanceOf(JsonEnumSchema.class);
        assertThat(spec.parameters().required()).containsExactlyInAnyOrder("name", "priority");
    }

    @Test
    @DisplayName("Should convert tool with deeply nested object structure")
    void shouldConvertToolWithDeeplyNestedObjects() {
        // Given
        List<Tool.Parameter> innerProperties = Collections.singletonList(
                Tool.Parameter.string("inner_value", "Inner value"));

        List<Tool.Parameter> outerProperties = Arrays.asList(
                Tool.Parameter.string("outer_value", "Outer value"),
                new Tool.Parameter("inner", Tool.ParameterType.OBJECT, "Inner object", true, null, innerProperties,
                        null));

        Tool.Definition definition = Tool.Definition.create(
                "nested_tool",
                "Tool with nested objects",
                Tool.InputSchema.of(
                        new Tool.Parameter("data", Tool.ParameterType.OBJECT, "Data object", true, null,
                                outerProperties, null)));
        Tool tool = createMockTool(definition);

        // When
        ToolSpecification spec = converter.toLangChain4j(tool);

        // Then
        assertThat(spec.parameters().properties().get("data")).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema dataSchema = (JsonObjectSchema) spec.parameters().properties().get("data");
        assertThat(dataSchema.properties().get("inner")).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema innerSchema = (JsonObjectSchema) dataSchema.properties().get("inner");
        assertThat(innerSchema.properties()).containsKey("inner_value");
    }

    @Test
    @DisplayName("Should convert tool with array of objects")
    void shouldConvertToolWithArrayOfObjects() {
        // Given
        List<Tool.Parameter> itemProperties = Arrays.asList(
                Tool.Parameter.string("id", "Item ID"),
                Tool.Parameter.integer("quantity", "Quantity"));

        // Create a parameter representing an array of objects
        // Note: This requires the item type to be OBJECT, but we need to represent the object structure
        Tool.Definition definition = Tool.Definition.create(
                "process_orders",
                "Processes multiple orders",
                Tool.InputSchema.of(
                        new Tool.Parameter("items", Tool.ParameterType.ARRAY, "Order items", true, null, null,
                                Tool.ParameterType.OBJECT)));
        Tool tool = createMockTool(definition);

        // When
        ToolSpecification spec = converter.toLangChain4j(tool);

        // Then
        assertThat(spec.parameters().properties().get("items")).isInstanceOf(JsonArraySchema.class);
        JsonArraySchema arraySchema = (JsonArraySchema) spec.parameters().properties().get("items");
        assertThat(arraySchema.items()).isInstanceOf(JsonObjectSchema.class);
    }

    @Test
    @DisplayName("Should preserve parameter descriptions")
    void shouldPreserveParameterDescriptions() {
        // Given
        String paramDescription = "This is a detailed description of the parameter";
        Tool.Definition definition = Tool.Definition.create(
                "test_tool",
                "Test tool",
                Tool.InputSchema.of(
                        Tool.Parameter.string("param", paramDescription, true)));
        Tool tool = createMockTool(definition);

        // When
        ToolSpecification spec = converter.toLangChain4j(tool);

        // Then
        JsonStringSchema paramSchema = (JsonStringSchema) spec.parameters().properties().get("param");
        assertThat(paramSchema.description()).isEqualTo(paramDescription);
    }

    @Test
    @DisplayName("Should handle tool with no parameters")
    void shouldHandleToolWithNoParameters() {
        // Given
        Tool.Definition definition = Tool.Definition.create(
                "simple_action",
                "Performs a simple action",
                Tool.InputSchema.empty());
        Tool tool = createMockTool(definition);

        // When
        ToolSpecification spec = converter.toLangChain4j(tool);

        // Then
        assertThat(spec.name()).isEqualTo("simple_action");
        assertThat(spec.description()).isEqualTo("Performs a simple action");
        assertThat(spec.parameters().properties()).isEmpty();
        assertThat(spec.parameters().required()).isEmpty();
    }

    // Error Handling Tests

    @Test
    @DisplayName("Should throw exception when converting null tool")
    void shouldThrowExceptionForNullTool() {
        assertThatThrownBy(() -> converter.toLangChain4j(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Embabel tool cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when converting null input schema")
    void shouldThrowExceptionForNullInputSchema() {
        assertThatThrownBy(() -> converter.convertInputSchema(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("InputSchema cannot be null");
    }

    // Helper Methods

    /**
     * Creates a mock Tool with the given definition.
     * Since Tool is an interface, we create a simple implementation for testing.
     */
    private Tool createMockTool(Tool.Definition definition) {
        return new Tool() {
            @Override
            public Tool.Definition getDefinition() {
                return definition;
            }

            @Override
            public Result call(String input) {
                throw new UnsupportedOperationException("Not implemented for test");
            }
        };
    }
}