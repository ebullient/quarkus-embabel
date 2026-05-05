package io.quarkiverse.embabel.agent.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.quarkiverse.embabel.agent.runtime.loop.QuarkusToolLoopFactory;
import io.quarkiverse.embabel.agent.runtime.provider.QuarkusModelProvider;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Integration test to verify that extension beans are properly registered
 * and discoverable via CDI.
 * <p>
 * Tests Step 20: Bean Registration Processor
 * Tests Step 21: Spring @Configuration support (via quarkus-spring-di)
 * <p>
 * Note: MessageConverterImpl and ToolSpecificationConverterImpl are NOT CDI beans.
 * They are stateless utility classes instantiated directly with 'new' in QuarkusLlmService.
 */
class BeanRegistrationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestConfiguration.class, TestBean.class))
            // Minimal config to enable the extension
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.model-name", "gpt-4o")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "http://localhost:8080/mock");

    @Inject
    QuarkusModelProvider modelProvider;

    @Inject
    QuarkusToolLoopFactory toolLoopFactory;

    @Inject
    TestBean testBean;

    /**
     * Test that extension beans registered by the BeanRegistrationProcessor
     * are discoverable and injectable via CDI.
     */
    @Test
    void testExtensionBeansAreRegistered() {
        assertThat(modelProvider)
                .as("QuarkusModelProvider should be injectable")
                .isNotNull();

        assertThat(toolLoopFactory)
                .as("QuarkusToolLoopFactory should be injectable")
                .isNotNull();
    }

    /**
     * Test that Spring @Configuration and @Bean work via quarkus-spring-di.
     * This verifies Step 21 - that Spring-style configuration is supported.
     */
    @Test
    void testSpringConfigurationSupport() {
        assertThat(testBean)
                .as("Bean created by Spring @Configuration should be injectable")
                .isNotNull();

        assertThat(testBean.getMessage())
                .as("Bean should have correct value from @Bean method")
                .isEqualTo("Test bean from Spring @Configuration");
    }

    /**
     * Test Spring @Configuration class to verify quarkus-spring-di support.
     */
    @Configuration
    static class TestConfiguration {

        @Bean
        TestBean testBean() {
            return new TestBean("Test bean from Spring @Configuration");
        }
    }

    /**
     * Simple test bean created by Spring @Bean method.
     */
    static class TestBean {
        private final String message;

        TestBean(String message) {
            this.message = message;
        }

        String getMessage() {
            return message;
        }
    }
}