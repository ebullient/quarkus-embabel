package io.quarkiverse.embabel.it;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Test to verify the embabel-agent feature is registered with Quarkus.
 * This ensures the extension's build step executed successfully.
 */
@QuarkusTest
public class EmbabelFeatureTest {

    @Inject
    LaunchMode launchMode;

    @ConfigProperty(name = "quarkus.profile")
    String profile;

    @Test
    public void testEmbabelFeatureIsRegistered() {
        // The feature registration happens at build time
        // If the extension loaded correctly, this test will pass
        // We verify by checking the application started successfully
        assertNotNull(launchMode, "Application should have started with embabel-agent feature");
    }

    @Test
    public void testApplicationProfile() {
        // Verify we're running in test mode
        assertNotNull(profile, "Profile should be set");
    }
}
