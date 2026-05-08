package io.quarkiverse.embabel.agent.runtime.llm;

import java.util.Objects;

import com.embabel.agent.spi.support.OutputConverter;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Quarkus implementation of {@link OutputConverter} that uses Jackson for JSON conversion.
 * <p>
 * This converter handles common LLM output formatting issues:
 * <ul>
 * <li>Removes markdown code blocks (```json, ```)</li>
 * <li>Fixes malformed escaped quotes that LLMs sometimes generate</li>
 * <li>Uses lenient JSON parsing to handle trailing commas, single quotes, unquoted field names, and comments</li>
 * <li>Parses JSON into the target type using Jackson</li>
 * </ul>
 * <p>
 * The lenient parsing features and malformed quote fixing are based on
 * {@code com.embabel.common.ai.converters.JacksonOutputConverter} from the Embabel framework,
 * adapted to work without Spring AI dependencies.
 *
 * @param <O> the output type to convert to
 */
public class QuarkusOutputConverter<O> implements OutputConverter<O> {

    private final Class<O> outputClass;
    private final ObjectMapper lenientMapper;

    /**
     * Creates a new output converter with lenient JSON parsing enabled.
     * <p>
     * The lenient mapper enables:
     * <ul>
     * <li>ALLOW_TRAILING_COMMA: {@code {"a": 1,}} is valid</li>
     * <li>ALLOW_SINGLE_QUOTES: {@code {'a': 'b'}} is valid</li>
     * <li>ALLOW_UNQUOTED_FIELD_NAMES: {@code {a: "b"}} is valid</li>
     * <li>ALLOW_JAVA_COMMENTS: {@code {"a": 1 /* comment * /}} is valid</li>
     * <li>ALLOW_YAML_COMMENTS: {@code {"a": 1 # comment}} is valid</li>
     * </ul>
     *
     * @param outputClass the target class to convert to
     * @param objectMapper the Jackson ObjectMapper for JSON parsing
     */
    public QuarkusOutputConverter(Class<O> outputClass, ObjectMapper objectMapper) {
        this.outputClass = Objects.requireNonNull(outputClass, "outputClass cannot be null");
        Objects.requireNonNull(objectMapper, "objectMapper cannot be null");

        // Create a lenient mapper that can handle common JSON formatting issues from LLMs
        this.lenientMapper = objectMapper.copy()
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature())
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature())
                .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature())
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature())
                .enable(JsonReadFeature.ALLOW_YAML_COMMENTS.mappedFeature());
    }

    /**
     * Converts LLM text output to a typed object.
     * <p>
     * This method:
     * <ol>
     * <li>Strips markdown code block markers (```json, ```)</li>
     * <li>Fixes malformed escaped quotes</li>
     * <li>Trims whitespace</li>
     * <li>Parses JSON using lenient Jackson parser</li>
     * <li>Returns the typed object</li>
     * </ol>
     *
     * @param text the LLM response text (may contain markdown)
     * @return the parsed object
     * @throws RuntimeException if JSON parsing fails
     */
    @Override
    public O convert(String text) {
        Objects.requireNonNull(text, "text cannot be null");

        try {
            String cleanText = unwrapJson(text);
            return lenientMapper.readValue(cleanText, outputClass);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to convert LLM output to " + outputClass.getName() + ": " + e.getMessage(),
                    e);
        }
    }

    /**
     * Returns the format hint for the LLM.
     * This tells the LLM to respond in JSON format.
     *
     * @return "json"
     */
    @Override
    public String getFormat() {
        return "json";
    }

    /**
     * Removes markdown code blocks and fixes malformed JSON from LLM output.
     * <p>
     * LLMs often wrap JSON in markdown code blocks like:
     *
     * <pre>
     * ```json
     * {"key": "value"}
     * ```
     * </pre>
     *
     * This method strips those markers and fixes common formatting issues to get valid JSON.
     */
    private String unwrapJson(String text) {
        String result = text.trim();

        // Remove markdown code blocks
        if (result.startsWith("```") && result.endsWith("```")) {
            result = result.replaceFirst("^```json", "")
                    .replaceFirst("^```", "")
                    .replaceFirst("```$", "")
                    .trim();
        }

        // Fix malformed escaped quotes - this is the one issue Jackson can't handle
        // because "key": \"value\" is fundamentally broken syntax
        result = fixMalformedEscapedQuotes(result);

        return result;
    }

    /**
     * Fix malformed JSON where the LLM has incorrectly escaped quote characters
     * that should be JSON string delimiters.
     * <p>
     * This fixes cases like: {@code "span": \"Glazunov's violin concerto\",}
     * where the LLM escapes the quotes that delimit the string value itself.
     * <p>
     * Note: Jackson's lenient features can't handle this because the backslash
     * before the opening quote makes it syntactically invalid in a way no parser
     * can interpret correctly.
     */
    private String fixMalformedEscapedQuotes(String json) {
        return json
                .replaceAll(":\\s*\\\\\"", ": \"") // Fix : \" -> : "
                .replaceAll("\\\\\",", "\",") // Fix \", -> ",
                .replaceAll("\\\\\"(\\s*})", "\"$1") // Fix \" } -> " }
                .replaceAll("\\\\\"(\\s*])", "\"$1"); // Fix \" ] -> " ]
    }

    @Override
    public String toString() {
        return "QuarkusOutputConverter{" +
                "outputClass=" + outputClass.getName() +
                ", format=json" +
                ", lenient=true" +
                '}';
    }
}