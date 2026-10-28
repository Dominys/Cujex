# Order-service example

> A complete, runnable Cucumber + JEXL e2e suite built on [`cujex-core`](../cujex-core/README.md)
> and [`cujex-kafka`](../cujex-kafka/README.md). Use it as the template for your own suite.

This aggregator groups the `order-service-*` modules: a tiny Spring Boot service, its
pure API model, a declarative HTTP client, and the test suite that drives the service
over REST and Kafka. For framework concepts and the extension recipe, read the
[cujex-core docs](../cujex-core/README.md); this page covers the **example project**.

## Modules

| Module | Packaging | Role |
|--------|-----------|------|
| `order-service-api-model` | jar | Pure API model records (`api.model`) — Jackson + Lombok, **no Spring**. Any client can reuse the exact wire model. Also holds the Kafka wire types and topic names. |
| `order-service-api-client` | jar | The **declarative HTTP client** (`api.client`): the `@HttpExchange` `OrderApi`, a factory, and **Spring Boot auto-configuration**. Depends on the model module. The only production artifact the tests depend on. |
| `order-service` | jar (Boot) | A tiny Spring Boot web app exposing `/orders`. **Publishes** order events and **consumes** status-update commands over Kafka. Packaged into a Docker image. Depends on `order-service-api-model`. |
| `order-service-it` | jar | The **example** Cucumber + JEXL test suite. Depends on `cujex-core`, `cujex-kafka` and `order-service-api-client` at **test scope** only, adds its own order-specific glue (`OrderSteps`, `OrderKafkaSteps`, `OrderTypes`), and starts the service + Kafka containers. |

Because `order-service-it` never has `order-service` on its classpath, the compiler
physically prevents a test from reaching into service internals — the only way to
interact with the service is through the REST client.

## Layout

```
examples/
├── pom.xml                                   aggregator (pom) — groups the modules below
├── order-service-api-model/
│   └── …/api/model/   Order, Customer, OrderStatus          records, no Spring
│                      OrderEvent, OrderEventType, StatusUpdateCommand, OrderTopics
├── order-service-api-client/
│   └── …/api/client/  OrderApi                              @HttpExchange REST interface
│                      OrderServiceClient                    builds the proxy from a base URL
│                      OrderServiceClientProperties          @ConfigurationProperties("order-service")
│                      OrderServiceClientAutoConfiguration   @AutoConfiguration exposing OrderApi
├── order-service/
│   ├── Dockerfile                            eclipse-temurin:21-jre, COPY target/*.jar
│   └── …/service/     Application, OrderController, OrderService
│                      KafkaConfig, OrderEventPublisher, OrderStatusListener
└── order-service-it/
    ├── …/RunCucumberIT.java                  JUnit Platform suite; glue = example + cujex packages
    ├── …/harness/
    │     ServiceUnderTest.java               base URL (container or -Dservice.baseUrl); joins Kafka network
    │     KafkaUnderTest.java                 starts the broker before the service
    │     TestConfig.java                     @Import(CujexCoreConfig, CujexKafkaConfig) + OrderApi + CujexKafkaSettings
    │     CucumberSpringConfiguration.java    @CucumberContextConfiguration + reset hook
    ├── …/cucumber/OrderTypes.java            EXTENSION POINT: order/customer @ParameterType + @DataTableType
    ├── …/steps/      OrderSteps, OrderKafkaSteps
    ├── resources/features/                   01…07 feature files (see below)
    └── resources/stubs/order_request.json    shared "success request" stub
```

## How the test talks to the service

### The declarative client (`order-service-api-client`)

The client is an **HTTP interface**: `OrderApi` describes the REST contract with
annotations, and Spring generates a proxy that performs the actual calls. No hand-written
`RestClient` plumbing in the tests.

```java
@HttpExchange(url = "/orders", accept = "application/json", contentType = "application/json")
public interface OrderApi {
    @PostExchange                 Order create(@RequestBody Order order);
    @GetExchange("/{id}")         Order get(@PathVariable("id") String id);
    @PutExchange("/{id}/status")  Order updateStatus(@PathVariable("id") String id,
                                                     @RequestParam("status") OrderStatus status);
    @GetExchange                  List<Order> getAll();
    @DeleteExchange               void deleteAll();
}
```

`OrderServiceClient.create(baseUrl)` wires `RestClient` → `RestClientAdapter` →
`HttpServiceProxyFactory` and returns a ready `OrderApi`.

> **Tip:** always give `@PathVariable`/`@RequestParam` an explicit name. Parameter names
> are not retained in bytecode unless compiled with `-parameters`, and the client module
> is consumed as a plain jar — explicit names keep the client robust.

### Spring Boot auto-configuration

The client module also ships **Spring Boot auto-configuration**, built the way the Spring
Boot docs recommend for a library: an `@AutoConfiguration` class registered in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
(never component-scanned), guarded by conditions so it stays out of the way.

A **Spring Boot** consumer then needs nothing but this jar on the classpath and one
property — no client wiring code:

```yaml
order-service:
  base-url: http://localhost:8080
```

`OrderServiceClientAutoConfiguration` reads `order-service.base-url`
(`OrderServiceClientProperties`) and contributes an `OrderApi` bean. It is
`@ConditionalOnProperty` (dormant until the base URL is set) and
`@ConditionalOnMissingBean` (backs off if the application defines its own `OrderApi`).
Those conditions are unit-tested with Spring Boot's `ApplicationContextRunner`
(`OrderServiceClientAutoConfigurationTest`) — property set → bean present, missing →
dormant, user bean → backs off — without starting a server or making any HTTP call.

> This e2e suite uses a **plain** Spring context (`cucumber-spring`, no Boot), so it
> wires the bean explicitly in `TestConfig` rather than through auto-configuration. The
> auto-configuration exists for real Spring Boot consumers of the client.

### Starting the service (`ServiceUnderTest`)

`ServiceUnderTest.baseUrl()` either returns the `-Dservice.baseUrl` override or lazily
builds the image from `../order-service/Dockerfile` and starts **one** container for the
whole suite, waiting on `/actuator/health` before handing back the mapped URL.
`TestConfig` turns that URL into the `OrderApi` bean the steps autowire:

```java
@Bean
OrderApi orderApi() {
    return OrderServiceClient.create(ServiceUnderTest.baseUrl());
}
```

Between scenarios, `CucumberSpringConfiguration`'s `@Before` hook calls
`orderApi.deleteAll()` so each scenario starts from a clean service — state reset over
the wire, no access to the service's memory.

## Feature files

| Feature | Demonstrates |
|---------|--------------|
| `01_context_storage.feature` | store / reference / read-back loop ([concept 1](../cujex-core/docs/scenario-context.md)) |
| `02_jexl_preprocessing.feature` | functions, literals, nested navigation ([concept 2](../cujex-core/docs/jexl-preprocessing.md)) |
| `03_field_editing.feature` | edit selected fields of a stored entity |
| `04_end_to_end.feature` | capstone tying it all together |
| `05_json_templates.feature` | load a stub, override only relevant fields ([concept 3](../cujex-core/docs/json-templates.md)) |
| `06_custom_jexl_functions.feature` | custom functions + namespace registration ([extending](../cujex-core/docs/extending.md#add-a-jexl-function)) |
| `07_kafka_messaging.feature` | produce/consume round-trips over Kafka ([cujex-kafka](../cujex-kafka/README.md)) |

## Running

### Full suite (builds the image, starts containers)

```bash
./mvnw verify                       # from the repo root — builds every module, runs all features
```

An HTML report is written to `order-service-it/target/cucumber.html`.

**Requirements:** JDK 21 (Maven via the `./mvnw` wrapper) and a running **Docker** daemon
(the suite uses Testcontainers).

### Docker on Rancher Desktop / Colima

With Docker Desktop no extra configuration is needed. If you use **Rancher Desktop** or
**Colima**, Testcontainers may not auto-detect the socket; export these first:

```bash
export DOCKER_HOST="unix://$HOME/.rd/docker.sock"          # Colima: unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
```

### Running from IntelliJ IDEA (macOS + Rancher Desktop / Colima)

IDEA launches tests with its own environment, not your shell's, so the same socket
problem shows up as `Could not find a valid Docker environment`. These are **environment
variables**, not JVM `-D` arguments. Add them to the run configuration (**Run → Edit
Configurations… → your JUnit/Cucumber config → Environment variables**), one per row:

| Name | Value (Rancher Desktop) | Value (Colima) |
|------|-------------------------|----------------|
| `DOCKER_HOST` | `unix:///Users/<you>/.rd/docker.sock` | `unix:///Users/<you>/.colima/default/docker.sock` |
| `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` | `/var/run/docker.sock` | `/var/run/docker.sock` |
| `TESTCONTAINERS_RYUK_DISABLED` | `true` | `true` |

- `DOCKER_HOST` points Testcontainers at the real socket;
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` is the path mounted **into** containers (Ryuk,
  the client); disabling Ryuk avoids its own socket probing.
- To avoid retyping, edit the **template** (the greyed-out *JUnit* / *Cucumber Java* entry
  under *Edit Configurations…*) so every new run inherits these values.
- Alternatively, launch IDEA from a terminal that already `export`ed the variables above
  (e.g. `open -a "IntelliJ IDEA"` from that shell) and it will inherit them.

### Run against an already-running service

To skip the container and point the suite at any running instance:

```bash
./mvnw -pl examples/order-service-it verify -Dservice.baseUrl=http://localhost:8080
```

See `harness/ServiceUnderTest.java` — `-Dservice.baseUrl` short-circuits the container.

## Notes & gotchas

- **Tests cannot import service classes.** This is enforced by the module graph:
  `order-service-it` only has `order-service-api-client` (and its transitive
  `order-service-api-model`) on its classpath. Interact via `OrderApi`.
- **Reactor build order matters.** `./mvnw verify` from the root builds the service jar
  *before* the IT module builds the Docker image from it. Running the IT module in
  isolation requires the service jar to already be installed.
- **Jackson across the boundary.** The service (Boot 4) serializes with Jackson 3; the
  tests use Jackson 2. Records round-trip over plain JSON, so the two sides interoperate
  without sharing a Jackson version.
- **No Spring Boot in the tests.** A plain Spring `@Configuration` + `@ContextConfiguration`
  keeps the test context light while preserving the `cucumber-glue` scope.
