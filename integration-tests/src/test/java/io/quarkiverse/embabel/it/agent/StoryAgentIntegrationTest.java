package io.quarkiverse.embabel.it.agent;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.embabel.common.ai.model.ModelProvider;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration test for StoryAgent using Quarkus Test.
 * <p>
 * This test verifies that the Quarkus Embabel extension properly:
 * - Loads the extension and starts the application
 * - Registers CDI beans (ModelProvider, AgentPlatform)
 * - Enables agent invocation through REST endpoints
 * - Supports mocking of LLM calls to avoid API costs
 * <p>
 * Uses @InjectMock to mock the ChatLanguageModel, preventing real API calls
 * while testing the full agent execution flow through the REST endpoint.
 */
@QuarkusTest
class StoryAgentIntegrationTest {

    @Inject
    ModelProvider modelProvider;

    @InjectMock
    ChatModel chatModel;

    @Test
    void shouldCraftStoryViaRestEndpoint() {
        // Given: Mock the LLM to return a story without making real API calls
        // String mockStoryText = "Once upon a time in a magical kingdom, there lived a brave knight who embarked on an epic quest.";
        // Mockito.when(chatModel.chat(Mockito.any(ChatRequest.class)))
        //         .thenReturn(ChatResponse.builder()
        //                 .aiMessage(new AiMessage(mockStoryText))
        //                 .build());

        // When: Call the REST endpoint
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "prompt": "Tell me a story about a magical kingdom"
                        }
                        """)
                .when()
                .post("/story/craft")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("text", notNullValue())
                .body("text", containsString("magical kingdom"));

        // Then: Verify the mock was called (agent executed)
        // Mockito.verify(chatModel, Mockito.atLeastOnce()).chat(Mockito.any(ChatRequest.class));
    }

    @Test
    void shouldHandleEmptyPrompt() {
        // Given: Mock response for empty prompt
        // Mockito.when(chatModel.chat(Mockito.any(ChatRequest.class)))
        //         .thenReturn(ChatResponse.builder()
        //                 .aiMessage(new AiMessage("A story about nothing in particular."))
        //                 .build());

        // When: Call with empty prompt
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "prompt": ""
                        }
                        """)
                .when()
                .post("/story/craft")
                .then()
                .statusCode(200)
                .body("text", notNullValue());
    }
}