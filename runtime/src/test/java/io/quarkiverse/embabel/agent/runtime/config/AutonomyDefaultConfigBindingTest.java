package io.quarkiverse.embabel.agent.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.embabel.agent.api.common.autonomy.AutonomyProperties;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AutonomyDefaultConfigBindingTest {

    @Inject
    AutonomyProperties autonomyProperties;

    @Test
    void shouldUseDefaultAutonomyProperties() {
        assertThat(autonomyProperties.getAgentConfidenceCutOff())
                .as("Agent confidence cut-off should default to 0.6")
                .isEqualTo(0.6);
        assertThat(autonomyProperties.getGoalConfidenceCutOff())
                .as("Goal confidence cut-off should default to 0.6")
                .isEqualTo(0.6);
    }
}
