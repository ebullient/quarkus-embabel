package io.quarkiverse.embabel.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThan;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration test for the chat endpoint.
 * <p>
 * This test verifies that the Quarkus Embabel extension properly:
 * <ul>
 * <li>Creates chat sessions with conversations</li>
 * <li>Handles user messages</li>
 * <li>Maintains conversation history</li>
 * <li>Provides conversation retrieval</li>
 * </ul>
 */
@QuarkusTest
class ChatIntegrationTest {

    @Test
    void shouldCreateChatSession() {
        // When: Create a new chat session
        given()
                .when()
                .post("/chat/session")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("sessionId", notNullValue())
                .body("messageCount", is(0));
    }

    @Test
    void shouldSendMessageToSession() {
        // Given: A chat session
        String sessionId = given()
                .when()
                .post("/chat/session")
                .then()
                .statusCode(200)
                .extract()
                .path("sessionId");

        // When: Send a message
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "message": "Hello, how are you?"
                        }
                        """)
                .when()
                .post("/chat/session/" + sessionId + "/message")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("sessionId", is(sessionId))
                .body("message", notNullValue())
                .body("messageCount", greaterThan(0));
    }

    @Test
    void shouldMaintainConversationHistory() {
        // Given: A chat session with messages
        String sessionId = given()
                .when()
                .post("/chat/session")
                .then()
                .statusCode(200)
                .extract()
                .path("sessionId");

        // Send first message
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "message": "First message"
                        }
                        """)
                .when()
                .post("/chat/session/" + sessionId + "/message")
                .then()
                .statusCode(200);

        // Send second message
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "message": "Second message"
                        }
                        """)
                .when()
                .post("/chat/session/" + sessionId + "/message")
                .then()
                .statusCode(200);

        // When: Get conversation history
        given()
                .when()
                .get("/chat/session/" + sessionId + "/history")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("sessionId", is(sessionId))
                .body("messageCount", is(2))
                .body("messages", notNullValue());
    }

    @Test
    void shouldHandleInvalidSession() {
        // When: Send message to non-existent session
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "message": "Hello"
                        }
                        """)
                .when()
                .post("/chat/session/invalid-session-id/message")
                .then()
                .statusCode(500); // IllegalArgumentException results in 500
    }

    @Test
    void shouldGetHistoryForEmptySession() {
        // Given: A new chat session with no messages
        String sessionId = given()
                .when()
                .post("/chat/session")
                .then()
                .statusCode(200)
                .extract()
                .path("sessionId");

        // When: Get conversation history
        given()
                .when()
                .get("/chat/session/" + sessionId + "/history")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("sessionId", is(sessionId))
                .body("messageCount", is(0));
    }

    @Test
    void shouldHandleMultipleSessions() {
        // Given: Two different chat sessions
        String sessionId1 = given()
                .when()
                .post("/chat/session")
                .then()
                .statusCode(200)
                .extract()
                .path("sessionId");

        String sessionId2 = given()
                .when()
                .post("/chat/session")
                .then()
                .statusCode(200)
                .extract()
                .path("sessionId");

        // When: Send messages to both sessions
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "message": "Message to session 1"
                        }
                        """)
                .when()
                .post("/chat/session/" + sessionId1 + "/message")
                .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "message": "Message to session 2"
                        }
                        """)
                .when()
                .post("/chat/session/" + sessionId2 + "/message")
                .then()
                .statusCode(200);

        // Then: Each session should have its own history
        given()
                .when()
                .get("/chat/session/" + sessionId1 + "/history")
                .then()
                .statusCode(200)
                .body("messageCount", is(1));

        given()
                .when()
                .get("/chat/session/" + sessionId2 + "/history")
                .then()
                .statusCode(200)
                .body("messageCount", is(1));
    }
}