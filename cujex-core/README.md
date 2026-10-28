# cujex-core

> The reusable, domain-agnostic heart of **Cujex** (**Cu**cumber + **Jex**l). Ships the
> scenario-context store, the JEXL `${...}` engine, the JSON-template loader and a set of
> generic steps — and knows nothing about any particular domain.

A suite consumes `cujex-core` at test scope, imports one Spring config, adds the
framework glue package to its Cucumber glue path, and then contributes only its own
domain glue. See [Extending Cujex](docs/extending.md) for the full recipe and the
`examples/order-service-it` module for a complete worked suite.

## Core concepts

Cujex is built on three ideas; read them in order:

1. **[Scenario context storage](docs/scenario-context.md)** — per-scenario memory: steps
   store results by key and reference them later, from Java or from the feature.
2. **[JEXL preprocessing](docs/jexl-preprocessing.md)** — every table cell / `{param}` is
   run through a JEXL engine, so `${created.id()}` navigates stored entities inline.
3. **[JSON templates](docs/json-templates.md)** — load a shared stub and override only
   the fields a scenario cares about, including a decomposition DSL for nested
   collections and a matching validation table.

Then: **[Extending Cujex](docs/extending.md)** — wire a new suite on, register domain
types, add steps, add JEXL functions (four ways), add endpoints.

## Integration quick-start

1. **Depend** on it at test scope:
   ```xml
   <dependency>
       <groupId>io.github.dominys</groupId>
       <artifactId>cujex-core</artifactId>
       <scope>test</scope>
   </dependency>
   ```
2. **Import** the Spring config from your `@CucumberContextConfiguration` context:
   ```java
   @CucumberContextConfiguration
   @Import(CujexCoreConfig.class)
   public class CucumberSpringConfiguration { }
   ```
3. **Add both glue packages** to the Cucumber glue path — your own plus the framework's:
   ```java
   @Suite
   @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.acme.it, io.github.dominys.cujex")
   public class RunCucumberIT { }
   ```

No Spring Boot is required; a plain Spring `@Configuration` + `@ContextConfiguration`
keeps the test context light while preserving the `cucumber-glue` scope. `cujex-core`
itself has **no Lombok dependency** (hand-written constructors).

## What's in the box

```
cujex-core/src/main/java/io/github/dominys/cujex/
├── CujexCoreConfig.java               the @Configuration you @Import
├── context/
│   ├── ScenarioContextStore.java      @Scope("cucumber-glue") key→value memory
│   ├── ScenarioContext.java           JEXL engine + ${...} resolution over the store
│   ├── CujexJexlContext.java          live-context handle passed to context-aware functions
│   └── StepVariables.java             "I define scenario variables:" backing type
├── cucumber/
│   ├── ParameterTypes.java            domain-agnostic type registry (toRecord/resolveRow)
│   ├── BaseCucRecord.java             @JsonUnwrapped cucId + payload split
│   ├── EditFieldsRecord.java          horizontal "edit fields" table support
│   ├── CucIdsToListDeserializer.java  ${a},${b} → List of stored entities
│   └── CucConstants.java              shared literals (NULL/empty markers, "index", …)
├── jexl/
│   ├── CucJexlFunctions.java          built-in default-namespace functions (now/uuid/…)
│   └── CujexJexlFunctions.java        SPI for consumer-contributed functions/namespaces
├── steps/
│   ├── BaseSteps.java                 base for domain steps (context + store accessors)
│   ├── InfraSteps.java                stub loading / scenario-variable steps
│   ├── TemplateSteps.java             "I create models from template …" (+ decomposition)
│   ├── ModelValidationSteps.java      "I expect models match:" table validation
│   └── GeneralValidationSteps.java    generic equals / field assertions
└── template/
    ├── JsonTemplate.java              stub loader + deep-copy/setPath override engine
    └── JsonStubRef.java               (cucId, file) row type for "I load JSON stubs:"
```

## Documentation index

| Doc | Topic |
|-----|-------|
| [docs/scenario-context.md](docs/scenario-context.md) | Concept 1 — per-scenario memory |
| [docs/jexl-preprocessing.md](docs/jexl-preprocessing.md) | Concept 2 — `${...}` resolution |
| [docs/json-templates.md](docs/json-templates.md) | Concept 3 — stubs, overrides, decomposition, validation |
| [docs/extending.md](docs/extending.md) | Wiring a suite, domain types, steps, JEXL functions, endpoints |

For asynchronous messaging, add the opt-in [`cujex-kafka`](../cujex-kafka/README.md)
extension.
