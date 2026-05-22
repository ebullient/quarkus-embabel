package com.embabel.template;

import jakarta.inject.Inject;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.template.agent.WriteAndReviewAgent;
import com.embabel.template.injected.InjectedDemo;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "demo", mixinStandardHelpOptions = true, description = "Quarkus Embabel Example Agent - demonstrates agent workflows")
public class DemoCommand implements Runnable {

    @Inject
    InjectedDemo injectedDemo;

    @Inject
    AgentPlatform agentPlatform;

    @Command(name = "story", description = "Generate and review a story about a topic")
    public void story(@Parameters(description = "Story topic or prompt", defaultValue = "caterpillars") String prompt) {
        // Illustrate calling an agent programmatically,
        // as most often occurs in real applications.
        var reviewedStory = AgentInvocation
                .create(agentPlatform, WriteAndReviewAgent.ReviewedStory.class)
                .invoke(new UserInput("Tell me a story about " + prompt));
        System.out.println(reviewedStory.getContent());
    }

    @Command(name = "animal", description = "Invent a fictional animal")
    public void animal() {
        System.out.println(injectedDemo.inventAnimal());
    }

    @Override
    public void run() {
        // Default command - show help
        CommandLine.usage(this, System.out);
    }
}