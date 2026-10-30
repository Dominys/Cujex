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

## How it compares

Most suites that test a service reach for one of the approaches below. Each trades
away either **fidelity** (are you testing the thing you ship?) or **maintainability**
(can a human still read and change the tests a year later?). Cujex is an attempt to
keep both.

| Common approach | The pain it brings | What Cujex does instead |
|-----------------|--------------------|-------------------------|
| **JUnit + `@SpringBootTest` / `MockMvc`** | Runs the service *in-process* and lets you swap pieces out with `@MockBean`, so you validate your own wiring — not the artifact you actually deploy. Real serialization, container config, startup and the HTTP/Kafka transport all go untested. | Drives the service as a true **black box**: it runs as the packaged Docker image (started by Testcontainers) and is reached only over REST/Kafka through a shared API artifact — the test never has the service classes on its classpath. |
| **Building request objects in Java** (builders / fixtures) | Every new field or case is a code change and a recompile, and only Java developers can read or write the tests. Fixture helpers sprawl and quietly drift from the model. | Scenarios are **Gherkin + data tables**. The request is a shared JSON stub; a scenario lists only the columns it overrides — no builder code, no recompile to add a case. |
| **One JSON file per scenario** | A folder of near-identical stubs — "JSON hell". You cannot see at a glance *what each scenario changes*, and one model change means editing dozens of files. | **One stub per model**, loaded once in a `Background`; each scenario overrides just the fields it exercises. What differs is the only thing on screen; the shared shape lives in a single place. |
| **Jinja2 / templated JSON** | Bolts a second language (loops, conditionals, includes) onto JSON. Logic hides inside templates, and now you debug the templating engine *and* the test. | No templating language. Overrides are **plain table columns** (`items[0].sku`, or the decomposed `items[] / .sku` form); nesting, appends and match-by-field are grammar, not code. |
| **Plain Cucumber with bespoke glue** | Every suite re-implements the same plumbing — a context store, cross-step references, JSON loading, generic assertions — a little differently each time, and maintains it forever. | Cujex *is* that plumbing, factored out and reusable: scenario context, `${...}` referencing, the template engine and generic create/validate steps ship behind one import. You add only domain steps. |

HTTP-focused DSLs such as **Karate** or **REST-assured** solve the transport side well,
but bring their own assertion/HTTP dialect and keep test data as JSON or script. Cujex
stays on **standard Gherkin** and your **real typed domain records** — the same
`api-model` and generated client your production code uses — so types, serialization and
the client library are exercised for real and shared, not re-described in a test-only
language.

### By the numbers

For a suite of ten scenarios that all vary the same base model (figures are
illustrative, from faithful equivalents of one representative scenario, token‑counted
with a GPT‑4‑class tokenizer):

- **Files to maintain:** one shared stub, versus **ten** near‑identical files in the
  one‑JSON‑per‑scenario approach.
- **Author‑written size:** per‑scenario JSON costs about **1.7× the tokens** of the
  equivalent Cujex override tables, and Java builders about **1.35×** — Cujex adds a
  short row per scenario while the base is written once.
- **Signal‑to‑noise:** a Cujex scenario lists *only the fields it changes* — four
  override cells here — while the file‑ and builder‑based styles restate the whole
  object every time. That is the ratio that matters for an AI agent: it regenerates a
  few explicit values instead of a full model it could silently corrupt.
- **Review effort:** a new scenario is a few‑row diff whose every line *is* a decision,
  so a reviewer checks only what changed. A new JSON file or builder shows up as a wall
  of mostly‑duplicated context, where the one meaningful difference has to be hunted for
  — the kind of diff that gets rubber‑stamped.

(Raw token count is close to a Jinja2 template, which wins on size alone — but only by
adding a second templating language and the maintenance that comes with it. The point of
Cujex is to stay small *and* keep everything in plain Gherkin against your real types.)

### Readable — and writable — by non-developers and AI agents

Because a scenario is Gherkin plus declarative tables, the people who define behaviour
can read and review it directly: product owners, analysts, QA, support. There is no Java
to decode in order to understand what a test asserts, and the columns name exactly the
fields a case depends on.

The same shape makes Cujex an unusually good target for **AI agents**. A constrained,
declarative grammar — a fixed set of steps plus table columns — is far easier for a model
to generate *correctly* than imperative setup code: there is less surface to get wrong,
every override is explicit, and a failure comes back in domain terms
(`${event.price()} equals ...`) rather than a stack trace, so an agent can read the
result and iterate.

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
| **[CHANGELOG.md](CHANGELOG.md)** | Release history and versioning policy ([Keep a Changelog](https://keepachangelog.com) + [SemVer](https://semver.org)). |
