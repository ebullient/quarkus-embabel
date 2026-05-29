# Quarkus Embabel Extension - Agent Guide

## Project Overview

The **Quarkus Embabel Extension** enables Embabel agents to run on Quarkus instead of Spring Boot, providing CDI-based agent lifecycle management and LangChain4j integration.

## Project Structure

```
quarkus-embabel/
├── runtime/              # Runtime module (CDI, lifecycle, LLM integration)
├── deployment/           # Build-time processing (bean discovery, validation)
├── docs/                 # Antora documentation
└── integration-tests/    # Integration test suite
```

## Key Technologies

- **Framework**: Quarkus (CDI, Arc)
- **LLM Integration**: LangChain4j
- **Core Agent Framework**: Embabel (Kotlin-based)
- **Build**: Maven

## Core Concepts

### Architecture Layers
1. **AgentPlatform (SPI)** - Environment for running agents
2. **AgentProcess** - Execution lifecycle management
3. **Planning System** - GOAP, Utility AI, Supervisor strategies
4. **Tool System** - LLM tool discovery and registration

### Key Annotations
- `@Agent` - Define multi-step agent
- `@Action` - Define agent step/capability
- `@AchievesGoal` - Mark terminal action
- `@LlmTool` / `@Tool` - Tools for LLM to use
- `@EmbabelComponent` - Components for Utility AI

### API vs SPI
**CRITICAL**: Application code should use only `com.embabel.agent.api.*` (API), never SPI directly.

## Development

### Building
```bash
mvn clean install              # Build all modules
mvn verify                     # Run tests including integration tests
mvn clean install -DskipTests  # Quick build without tests
```

### Testing
- **Integration tests**: Located in `integration-tests/` directory
  - `java-agent-template/` - Example agent implementations
  - `rest-api/` - REST endpoint examples
- Use `@QuarkusTest` to test runtime behavior with CDI
- Deployment tests validate build-time processing
- Example agents in `integration-tests/java-agent-template/src/main/java/com/embabel/template/agent/`

### Module Dependencies
```
deployment/ ──(depends on)──> runtime/
     │                            │
     │                            │
     └─── build-time ────────────┴─── runtime CDI beans
          (Processors,                 (QuarkusAgentDeployer,
           BuildItems)                  LlmServiceRecorder)
```

- **`deployment/`** depends on **`runtime/`** (compile scope) for build-time processing
- Applications depend only on **`runtime/`** (user scope)
- Test utilities may use both modules

### Common Patterns

#### Quarkus Extension Architecture
- **Build-time** (`deployment/`): Bean discovery, validation, bytecode generation
  - `EmbabelProcessor` - Main build processor
  - Uses Jandex for annotation scanning
  - Produces `BuildItem` instances consumed by Arc
- **Runtime** (`runtime/`): CDI beans, agent lifecycle, LLM integration
  - `QuarkusAgentDeployer` - CDI-based agent deployment
  - `LlmServiceRecorder` - LangChain4j integration recorder
  - `ChatBeansProducer` - Chat model bean production

#### Agent Definition Pattern
```java
@Agent(description = "...")
public class MyAgent {
    
    @Inject
    SomeDependency dependency;
    
    @Action
    public Result performAction(UserInput input) { }
    
    @AchievesGoal
    public FinalResult achieveGoal(IntermediateResult result) { }
}
```

### Pitfalls & Gotchas

**Critical:**
- ❌ **Never use SPI** (`com.embabel.agent.spi.*`) in application code - use only API
- ❌ **Spring AI is NOT supported** - this extension uses LangChain4j exclusively
- ✅ **Use CDI** (`@Inject`, `@ApplicationScoped`) as primary dependency injection
- ✅ **Spring DI compatibility available** if needed (`@Value` supported via `quarkus-spring-boot-properties`)

**Dependency Injection:**
- **Prefer CDI**: `@Inject`, `@ApplicationScoped`, `@RequestScoped`
- **Spring DI works** but is secondary: `@Autowired`, `@Component` (via compatibility layer)
- Don't mix paradigms unnecessarily - pick one approach per class

**Build-time vs Runtime:**
- Bean discovery happens at **build-time** in `deployment/` module
- Runtime behavior defined in CDI beans in `runtime/` module
- Configuration processing happens at build-time (Quarkus pattern)

**Dependencies:**
- Embabel core is Kotlin-based (kotlin-stdlib required)
- Reactor core used for streaming (keep it on classpath)
- Spring Retry kept for `RetryTemplate` compatibility
- JSON Schema generator required for LLM tool schemas
- Spring AI explicitly excluded - use LangChain4j instead

### Where to Find Examples
- `integration-tests/java-agent-template/src/main/java/com/embabel/template/agent/` - Agent implementations
- `integration-tests/java-agent-template/src/test/java/` - Test patterns
- `runtime/src/main/java/io/quarkiverse/embabel/agent/runtime/` - Extension implementation patterns

## Supported LLM Providers

Via LangChain4j integration:
- OpenAI
- Anthropic
- Ollama
- Others supported by LangChain4j

## Documentation

Documentation is maintained in `docs/` using Antora format and published to <https://docs.quarkiverse.io/>.