package com.embabel.template.injected;

import jakarta.validation.constraints.Pattern;

import org.springframework.stereotype.Component;

import com.embabel.agent.api.common.Ai;

/**
 * Demonstrate injection of Embabel's OperationContext into a Spring component.
 *
 * @param ai Embabel AI helper, injected by Spring
 */
@Component
public class InjectedDemo {

    final Ai ai;

    InjectedDemo(Ai ai) {
        this.ai = ai;
    }

    /**
     * Demonstrates use of JSR-380 validation annotations on record fields
     * to constrain generated content.
     */
    public record Animal(
            String name,
            @Pattern(regexp = ".*ox.*", message = "Species must contain 'ox'") String species) {
    }

    public Animal inventAnimal() {
        return ai
                .withDefaultLlm()
                .withId("invent-animal")
                .creating(Animal.class)
                .withExample("good example", new Animal("Fluffox", "Magicox"))
                .withExample("bad example: does not pass validation", new Animal("Sparky", "Dragon"))
                .fromPrompt("""
                        You just woke up in a magical forest.
                        Invent a fictional animal.
                        The animal should have a name and a species.
                        """);
    }
}
