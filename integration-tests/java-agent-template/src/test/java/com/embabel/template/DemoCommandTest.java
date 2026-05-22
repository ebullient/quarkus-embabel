package com.embabel.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;

@QuarkusMainTest
class DemoCommandTest {

    @Test
    @Launch({})
    void defaultShowsHelp(LaunchResult result) {
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.getOutput())
                .contains("story")
                .contains("animal");
    }

    @Test
    @Launch({ "story", "robots" })
    void storyCommandPrintsOutput(LaunchResult result) {
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.getOutput()).isNotBlank();
    }

    @Test
    @Launch({ "animal" })
    void animalCommandPrintsOutput(LaunchResult result) {
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.getOutput()).isNotBlank();
    }
}
