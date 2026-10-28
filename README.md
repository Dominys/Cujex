# Cujex

**Cujex** (**Cu**cumber + **Jex**l) is a small, reusable framework for writing
**e2e / service-level tests** with Cucumber. It adds a JEXL expression layer, a
per-scenario memory store, and a JSON-template engine on top of plain Cucumber, so
your feature files can build, reference, and validate real domain objects inline —
without a pile of bespoke glue code in every suite.

It is domain-agnostic: the core library knows nothing about any particular service.
You depend on it at test scope, import one Spring config, add its glue package to your
Cucumber glue path, and contribute only your own domain steps. A runnable
[`order-service` example](examples/README.md) in this repo shows the whole thing
end to end, including the optional Kafka extension.

## Why Cujex

Four patterns make up the framework — each is documented in depth under
[`cujex-core`](cujex-core/README.md):

1. **[Scenario context storage](cujex-core/docs/scenario-context.md)** — per-scenario
   key/value memory. Steps store their results by key and reference them later, from
   Java or straight from the feature file.
2. **[JEXL preprocessing](cujex-core/docs/jexl-preprocessing.md)** — every feature-file
   value shaped like `${ ... }` is evaluated as a JEXL expression *before* it reaches
   your step code, so `${created.id()}` navigates a previously stored entity inline.
3. **[JSON templates](cujex-core/docs/json-templates.md)** — load a shared request/response
   stub and override only the fields a scenario actually exercises, with a decomposition
   DSL for nested collections and a matching table-driven validation step.
4. **Asynchronous messaging** — the opt-in [`cujex-kafka`](cujex-kafka/README.md) extension
   adds parallel-safe produce/consume round-trips over Kafka.

Here is what that buys you in a feature file — stored entities, inline JEXL, and
generic steps, with no order-specific plumbing:

```gherkin
Scenario: Creating an order publishes an ORDER_CREATED event
  Given I prepare orders:
    | cucId  | price | status |
    | order1 | 150.0 | NEW    |
  When I create order ${order1} and store result in created
  Then I await an order event matching and store result in event:
    | type          | orderId         | status |
    | ORDER_CREATED | ${created.id()} | NEW    |
  And I expect that ${event.price()} equals ${150.0}
```

## Add Cujex to your suite

1. **Depend** on the core at test scope (currently `1.0-SNAPSHOT`, build from source —
   see [Building](#building)):
   ```xml
   <dependency>
       <groupId>io.github.dominys</groupId>
       <artifactId>cujex-core</artifactId>
       <scope>test</scope>
   </dependency>
   ```
2. **Import** the Spring config from your `@CucumberContextConfiguration` context:
   ```java
   @Import(CujexCoreConfig.class)
   ```
3. **Add both glue packages** to the Cucumber glue path — your own plus the framework's:
   ```java
   @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.acme.it, io.github.dominys.cujex")
   ```

No Spring Boot is required; a plain Spring `@Configuration` + `@ContextConfiguration`
keeps the test context light. The full recipe — registering domain types, adding steps,
contributing JEXL functions, wiring Kafka — lives in
[Extending Cujex](cujex-core/docs/extending.md).

## Modules

The reactor has two library modules plus a runnable example aggregator:

| Module | Packaging | Role |
|--------|-----------|------|
| [`cujex-core`](cujex-core/README.md) | jar | **The framework.** Domain-agnostic Cucumber + JEXL infrastructure (`io.github.dominys.cujex`): scenario context storage, per-cell JEXL preprocessing, typed data-table mapping, the JSON template engine, and the generic creation/validation steps. Any suite can depend on it. |
| [`cujex-kafka`](cujex-kafka/README.md) | jar | **Opt-in Kafka extension** (`io.github.dominys.cujex.kafka`): one long-lived consumer per topic, a fan-out bus, per-scenario filtering inboxes for parallel safety, a consumer-group-readiness wait, and a producer. Payload-agnostic (raw `byte[]`). Activated only by explicit `@Import`. |
| [`examples`](examples/README.md) | pom | **Demonstration only.** Aggregator for the runnable `order-service-*` project that drives a sample service over REST and Kafka using Cujex. Not part of the published library. |

## See it in action

The [`examples/`](examples/README.md) tree is a complete, runnable suite that uses Cujex
to test a sample `order-service` as a true **black box**: the service runs as a Spring
Boot app inside a Docker container (started automatically by Testcontainers) and the test
module talks to it only over **REST** and **Kafka** through a shared API artifact — it
never has the service classes on its classpath.

```
  ┌─────────────────────────┐          ┌────────────────────────────────────────┐
  │      cujex-core         │          │        order-service-api-model         │
  │ reusable Cucumber+JEXL  │          │        pure records, no Spring         │
  │ framework (io.github.   │          │              (api.model)               │
  │ dominys.cujex), no      │          └────────────────────┬───────────────────┘
  │ domain knowledge        │                               │
  └───────────┬─────────────┘    ┌──────────────────────────┴─────────────────────────┐
              │ test scope       ▼ depends on                            depends on   ▼
              │       ┌──────────────────────┐                  ┌────────────────────────────┐
              │       │    order-service     │                  │  order-service-api-client  │
              │       │ Spring Boot web app  │                  │   @HttpExchange client +   │
              │       │   (runs in Docker)   │                  │  Spring Boot auto-config   │
              │       └───────────┬──────────┘                  └──────────────┬─────────────┘
              │                   │                                            │ test scope
              │                   │                                            ▼
              │                   │                          ┌─────────────────┬────────────────┐
              │                   │ REST / HTTP              │         order-service-it         │
              │                   └─────────────────────────▶│    Cucumber + JEXL e2e tests     │
              └──────────────────────────────────────────────▶  Testcontainers starts service   │
                                                             └──────────────────────────────────┘
```

Run the whole example — build every module, package the service into a Docker image,
start it in a container, and run the feature files through the glue in
`io.github.dominys.cujex.examples`:

```bash
./mvnw verify
```

An HTML report is written to `examples/order-service-it/target/cucumber.html`. The
example's modules, feature files, and Docker/Rancher/IDE setup notes are documented in
[examples/README.md](examples/README.md).

## Building

```bash
./mvnw -DskipTests install   # build + install the library jars locally
./mvnw verify                # also run the example suite (needs Docker)
```

**Requirements:** JDK 21 (Maven is provided via the wrapper `./mvnw`). Running the
example suite additionally needs a running **Docker** daemon (Testcontainers). On
**Rancher Desktop** / **Colima**, or when running from IntelliJ, a little Testcontainers
socket configuration is needed — see
[examples/README.md → Running](examples/README.md#running).

## Documentation

| Where | What |
|-------|------|
| **[cujex-core/README.md](cujex-core/README.md)** | The framework: core concepts, integration quick-start, and a docs index. Start here to use Cujex in your own suite. |
| ├ [docs/scenario-context.md](cujex-core/docs/scenario-context.md) | Concept 1 — per-scenario memory |
| ├ [docs/jexl-preprocessing.md](cujex-core/docs/jexl-preprocessing.md) | Concept 2 — `${...}` resolution |
| ├ [docs/json-templates.md](cujex-core/docs/json-templates.md) | Concept 3 — stubs, overrides, decomposition, validation |
| └ [docs/extending.md](cujex-core/docs/extending.md) | Wiring a suite, domain types, steps, JEXL functions, endpoints |
| **[cujex-kafka/README.md](cujex-kafka/README.md)** | The opt-in Kafka extension: what it provides, wiring, container ordering, steps, payload-agnostic transport. |
| **[examples/README.md](examples/README.md)** | The order-service example: modules, how the test talks to the service, running details (Docker/Rancher/IDE), feature files, and gotchas. |
