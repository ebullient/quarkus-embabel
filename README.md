# Quarkus Embabel Extension

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse/quarkus-embabel-extension?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse/quarkus-embabel-extension-parent)

A Quarkus extension that enables [Embabel](https://embabel.com) agents to run on Quarkus with CDI-based lifecycle management and LangChain4j integration.

## What is Embabel?

Embabel is an agent framework that provides multi-step agent execution with planning strategies (GOAP, Utility AI, Supervisor) and LLM tool integration. Originally built for Spring Boot, this extension brings Embabel to the Quarkus ecosystem.

## Features

- 🔄 **CDI-based agent lifecycle** - Native Quarkus dependency injection (Spring DI also supported)
- 🤖 **LangChain4j integration** - Support for OpenAI, Anthropic, Ollama, and other LLM providers
- 📋 **Multiple planning strategies** - GOAP (Goal-Oriented Action Planning), Utility AI, Supervisor
- 🔧 **LLM tool discovery** - Automatic registration of tools for LLM to use
- ⚡ **Quarkus performance** - Build-time optimization and fast startup
- ☕ **Java & Kotlin support** - Embabel core is Kotlin-based, Java applications fully supported

## Installation

Add the extension to your Quarkus project:

**Maven:**
```xml
<dependency>
    <groupId>io.quarkiverse</groupId>
    <artifactId>quarkus-embabel-agent</artifactId>
    <version>${quarkus-embabel.version}</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'io.quarkiverse:quarkus-embabel-agent:${quarkusEmbabelVersion}'
```

You'll also need a LangChain4j model provider. For example, to use OpenAI:

```xml
<dependency>
    <groupId>io.quarkiverse.langchain4j</groupId>
    <artifactId>quarkus-langchain4j-openai</artifactId>
</dependency>
```

## Quick Start

### 1. Define an Agent

```java
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import jakarta.inject.Inject;

@Agent(description = "A simple greeting agent")
public class GreetingAgent {
    
    @Inject
    SomeService service;
    
    @Action
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
    
    @AchievesGoal
    public String createGreeting(String input) {
        return greet(input);
    }
}
```

### 2. Configure Your LLM Provider

In `application.properties`:

```properties
quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
quarkus.langchain4j.openai.chat-model.model-name=gpt-4
```

### 3. Deploy and Run

Agents are automatically discovered and deployed at startup. Use CDI to inject and run your agents:

```java
@Inject
AgentPlatform platform;

public void runAgent() {
    platform.deploy("greeting-agent", GreetingAgent.class);
    // Agent is now running and ready to process requests
}
```

## Key Concepts

### Annotations
- `@Agent` - Define a multi-step agent
- `@Action` - Define an agent step/capability
- `@AchievesGoal` - Mark a terminal action that completes the agent's goal
- `@LlmTool` / `@Tool` - Tools that the LLM can invoke
- `@EmbabelComponent` - Components for Utility AI

### Planning Strategies
- **GOAP** - Goal-Oriented Action Planning for complex goal hierarchies
- **Utility AI** - Score-based action selection
- **Supervisor** - LLM-guided planning and execution

### API vs SPI
**Important**: Application code should use only `com.embabel.agent.api.*` (API), never SPI directly.

## Examples

Check the `integration-tests/` directory for complete examples:
- **`java-agent-template/`** - Basic agent patterns and tests
- **`rest-api/`** - REST endpoint integration examples

## LLM Provider Support

Via LangChain4j integration:
- OpenAI (GPT-3.5, GPT-4, GPT-4o)
- Anthropic (Claude)
- Ollama (local models)
- Others - See [LangChain4j documentation](https://docs.langchain4j.dev/)

## Documentation

Full documentation is available at <https://docs.quarkiverse.io/>

## Contributing

Contributions are welcome! This extension is part of the Quarkiverse ecosystem.

- [Quarkiverse Contributing Guide](https://github.com/quarkiverse/quarkiverse/wiki)
- [Building Quarkus Extensions](https://quarkus.io/guides/building-my-first-extension)

## Requirements

- Java 21+
- Quarkus 3.x
- Maven or Gradle

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
