package io.quarkiverse.embabel.agent.runtime.tool;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import com.embabel.agent.api.tool.Tool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;

/**
 * Default implementation of ToolSpecificationConverter that converts Embabel Tool definitions
 * to LangChain4j ToolSpecification format.
 */
@ApplicationScoped
public class ToolSpecificationConverterImpl implements ToolSpecificationConverter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ToolSpecification toLangChain4j(Tool embabelTool) {
        if (embabelTool == null) {
            throw new IllegalArgumentException("Embabel tool cannot be null");
        }
        Tool.Definition def = embabelTool.getDefinition();
        return ToolSpecification.builder()
                .name(def.getName())
                .description(def.getDescription())
                .parameters(convertInputSchema(def.getInputSchema()))
                .build();
    }

    @Override
    public JsonObjectSchema convertInputSchema(Tool.InputSchema schema) {
        if (schema == null) {
            throw new NullPointerException("InputSchema cannot be null");
        }
        try {
            // Parse the JSON schema from Embabel
            String jsonSchema = schema.toJsonSchema();
            JsonNode schemaNode = objectMapper.readTree(jsonSchema);

            JsonObjectSchema.Builder builder = JsonObjectSchema.builder();

            // Extract description if present
            if (schemaNode.has("description")) {
                builder.description(schemaNode.get("description").asText());
            }

            // Extract properties
            if (schemaNode.has("properties")) {
                JsonNode propertiesNode = schemaNode.get("properties");
                propertiesNode.fields().forEachRemaining(entry -> {
                    String propertyName = entry.getKey();
                    JsonNode propertySchema = entry.getValue();
                    JsonSchemaElement element = convertProperty(propertySchema);
                    builder.addProperty(propertyName, element);
                });
            }

            // Extract required fields
            if (schemaNode.has("required")) {
                List<String> requiredFields = new ArrayList<>();
                schemaNode.get("required").forEach(node -> requiredFields.add(node.asText()));
                builder.required(requiredFields);
            }

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert InputSchema to JsonObjectSchema", e);
        }
    }

    /**
     * Converts a single property from JSON schema to JsonSchemaElement.
     */
    private JsonSchemaElement convertProperty(JsonNode propertySchema) {
        String type = propertySchema.has("type") ? propertySchema.get("type").asText() : "string";
        String description = propertySchema.has("description") ? propertySchema.get("description").asText() : null;

        return switch (type) {
            case "string" -> {
                if (propertySchema.has("enum")) {
                    List<String> enumValues = new ArrayList<>();
                    propertySchema.get("enum").forEach(node -> enumValues.add(node.asText()));
                    yield JsonEnumSchema.builder()
                            .enumValues(enumValues)
                            .description(description)
                            .build();
                } else {
                    yield description != null
                            ? JsonStringSchema.builder().description(description).build()
                            : new JsonStringSchema();
                }
            }
            case "integer" -> description != null
                    ? JsonIntegerSchema.builder().description(description).build()
                    : new JsonIntegerSchema();
            case "number" -> description != null
                    ? JsonNumberSchema.builder().description(description).build()
                    : new JsonNumberSchema();
            case "boolean" -> description != null
                    ? JsonBooleanSchema.builder().description(description).build()
                    : new JsonBooleanSchema();
            case "array" -> convertArrayProperty(propertySchema, description);
            case "object" -> convertObjectProperty(propertySchema, description);
            default -> new JsonStringSchema(); // Default to string for unknown types
        };
    }

    /**
     * Converts an array property from JSON schema.
     */
    private JsonArraySchema convertArrayProperty(JsonNode propertySchema, String description) {
        JsonArraySchema.Builder builder = JsonArraySchema.builder();
        if (description != null) {
            builder.description(description);
        }

        if (propertySchema.has("items")) {
            JsonNode itemsSchema = propertySchema.get("items");
            JsonSchemaElement itemElement = convertProperty(itemsSchema);
            builder.items(itemElement);
        }

        return builder.build();
    }

    /**
     * Converts an object property from JSON schema.
     */
    private JsonObjectSchema convertObjectProperty(JsonNode propertySchema, String description) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        if (description != null) {
            builder.description(description);
        }

        // Extract nested properties
        if (propertySchema.has("properties")) {
            JsonNode propertiesNode = propertySchema.get("properties");
            propertiesNode.fields().forEachRemaining(entry -> {
                String propertyName = entry.getKey();
                JsonNode nestedPropertySchema = entry.getValue();
                JsonSchemaElement element = convertProperty(nestedPropertySchema);
                builder.addProperty(propertyName, element);
            });
        }

        // Extract required fields for nested object
        if (propertySchema.has("required")) {
            List<String> requiredFields = new ArrayList<>();
            propertySchema.get("required").forEach(node -> requiredFields.add(node.asText()));
            builder.required(requiredFields);
        }

        return builder.build();
    }
}