package io.quarkiverse.embabel.agent.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.embabel.agent.api.common.autonomy.AutonomyProperties;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(AutonomyConfigBindingTest.CustomValuesProfile.class)
class AutonomyConfigBindingTest {

    public static class CustomValuesProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "embabel.agent.platform.autonomy.agent-confidence-cut-off", "0.8",
                    "embabel.agent.platform.autonomy.goal-confidence-cut-off", "0.75");
        }
    }

    @Inject
    AutonomyProperties autonomyProperties;

    @Test
    void shouldBindCustomAutonomyProperties() {
        assertThat(autonomyProperties.getAgentConfidenceCutOff())
                .as("Agent confidence cut-off should reflect configured value")
                .isEqualTo(0.8);
        assertThat(autonomyProperties.getGoalConfidenceCutOff())
                .as("Goal confidence cut-off should reflect configured value")
                .isEqualTo(0.75);
    }
}
