# it-tests-example

A small, self-contained example of how to write **e2e / service-level tests** with
**Cucumber** and a **JEXL** preprocessing layer. The infrastructure here is extracted
and decoupled from a real project (`banner-service-integration-tests`) so it can serve
as documented, runnable reference material.

Unlike a classic unit test, the test module here **cannot see the service classes at
all**. The service runs as a **Spring Boot web app inside a Docker container**
(started automatically with Testcontainers) and the tests talk to it only over
**REST** through a shared API artifact. That is what makes these true black-box,
over-the-wire service tests.

The two patterns it is built to teach:

1. **Scenario context storage** – steps store their results in a per-scenario
   key/value store and reference them later.
2. **JEXL preprocessing** – every feature-file value shaped like `${ ... }` is
   evaluated as an expression *before* it reaches your step code.

---

## Architecture

Four Maven modules in a reactor. The arrows show the **only** compile dependencies —
note the test module depends on the API contract, never on the service:

```
                      ┌────────────────────────────────────────┐
                      │        order-service-api-model         │
                      │        pure records, no Spring         │
                      │              (api.model)               │
                      └────────────────────┬───────────────────┘
                                           │
                ┌──────────────────────────┴─────────────────────────┐
                ▼ depends on                            depends on   ▼
    ┌──────────────────────┐                          ┌────────────────────────────┐
    │    order-service     │                          │  order-service-api-client  │
    │ Spring Boot web app  │                          │   @HttpExchange client +   │
    │   (runs in Docker)   │                          │  Spring Boot auto-config   │
    └───────────┬──────────┘                          └──────────────┬─────────────┘
                │                                                    │ test scope
                │                                                    ▼
                │                                  ┌─────────────────┬────────────────┐
                │ REST / HTTP                      │         order-service-it         │
                └─────────────────────────────────▶│    Cucumber + JEXL e2e tests     │
                                                   │  Testcontainers starts service   │
                                                   └──────────────────────────────────┘
```

| Module | Packaging | Role |
|--------|-----------|------|
| `order-service-api-model` | jar | Pure API model records (`api.model`) — Jackson + Lombok, **no Spring**. Any client, Spring or not, can reuse the exact wire model. |
| `order-service-api-client` | jar | The **declarative HTTP client** (`api.client`): the `@HttpExchange` `OrderApi`, a factory, and **Spring Boot auto-configuration**. Depends on the model module. The only production artifact the tests depend on. |
| `order-service` | jar (Boot) | A tiny Spring Boot web app exposing `/orders`. Packaged into a Docker image. Depends on `order-service-api-model`. |
| `order-service-it` | jar | The Cucumber + JEXL test suite. Depends on `order-service-api-client` at **test scope** only; starts the service container and drives it over REST. |

Because `order-service-it` never has `order-service` on its classpath, the compiler
physically prevents a test from reaching into service internals — the only way to
interact with the service is through the REST client.

---

## Quick start

```bash
./mvnw verify
```

This builds all three modules, packages the service into a Docker image, starts it
in a container, and runs the four feature files through the glue in
`org.dominys.ittests`. An HTML report is written to
`order-service-it/target/cucumber.html`.

**Requirements:**

- JDK 21 (Maven is provided via the wrapper `./mvnw`).
- A running **Docker** daemon (the suite uses Testcontainers).

### Docker on Rancher Desktop / Colima

With Docker Desktop no extra configuration is needed. If you use **Rancher Desktop**
or **Colima**, Testcontainers may not auto-detect the socket; export these first:

```bash
export DOCKER_HOST="unix://$HOME/.rd/docker.sock"          # Colima: unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
```

### Run against an already-running service

To skip the container and point the suite at any running instance:

```bash
./mvnw -pl order-service-it verify -Dservice.baseUrl=http://localhost:8080
```

See `harness/ServiceUnderTest.java` — `-Dservice.baseUrl` short-circuits the container.

---

## Project layout

```
pom.xml                                        # parent reactor: modules, BOMs, plugin mgmt

order-service-api-model/
  src/main/java/org/dominys/ittests/api/
    model/   Order.java, Customer.java, OrderStatus.java
                                               # records + Lombok @Builder/@Jacksonized, no Spring

order-service-api-client/
  src/main/java/org/dominys/ittests/api/
    client/  OrderApi.java                      #   @HttpExchange declarative REST interface
             OrderServiceClient.java            #   builds the client proxy from a base URL
             OrderServiceClientProperties.java  #   @ConfigurationProperties("order-service")
             OrderServiceClientAutoConfiguration.java  # @AutoConfiguration exposing OrderApi
  src/main/resources/META-INF/spring/
    ...AutoConfiguration.imports               #   registers the auto-configuration

order-service/
  Dockerfile                                   # eclipse-temurin:21-jre, COPY target/*.jar
  src/main/java/org/dominys/ittests/service/
    Application.java                            # @SpringBootApplication
    OrderController.java                        # @RestController mirroring OrderApi
    OrderService.java                           # in-memory store (the SUT logic)

order-service-it/
  src/test/java/org/dominys/ittests/
    RunCucumberIT.java                          # JUnit Platform suite (entry point)
    harness/                                    # boots the SUT + Spring wiring
      ServiceUnderTest.java                     #   resolves base URL (container or override)
      TestConfig.java                           #   Spring test context; provides OrderApi bean
      CucumberSpringConfiguration.java          #   @CucumberContextConfiguration + reset hook
    context/                                    # concept #1: scenario context storage
      ScenarioContextStore.java                 #   per-scenario key/value store
      ScenarioContext.java                      #   JEXL engine: ${...} resolution + editing
      StepVariables.java                        #   data-table type for "define variables"
    jexl/                                       # concept #2: JEXL preprocessing
      CucJexlFunctions.java                     #   custom JEXL functions (now(), uuid(), ...)
    cucumber/                                   # Cucumber parameter/data-table plumbing
      ParameterTypes.java                       #   ObjectMapper + @ParameterType/@DataTableType
      BaseCucRecord.java, EditFieldsRecord.java #   table-row <-> record mapping
      CucConstants.java, CucIdsToListDeserializer.java
    steps/
      BaseSteps.java                            #   access to context + store
      InfraSteps.java                           #   "I define scenario variables:"
      GeneralValidationSteps.java               #   generic assertions
      OrderSteps.java                           #   domain steps driving the REST client
  src/test/resources/features/
    01_context_storage.feature                  # store / reference / read-back loop
    02_jexl_preprocessing.feature               # functions, literals, nested navigation
    03_field_editing.feature                    # edit selected fields of a stored entity
    04_end_to_end.feature                       # capstone tying it all together
```

---

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

> **Tip:** always give `@PathVariable`/`@RequestParam` an explicit name. Parameter
> names are not retained in bytecode unless compiled with `-parameters`, and the client
> module is consumed as a plain jar — explicit names keep the client robust.

### Spring Boot auto-configuration

The client module also ships **Spring Boot auto-configuration**, built the way the
Spring Boot docs recommend for a library: an `@AutoConfiguration` class registered in
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
(`OrderServiceClientAutoConfigurationTest`), which exercises each branch — property set
→ bean present, property missing → dormant, user bean → backs off, and property binding —
without starting a server or making any HTTP call.

> This e2e suite uses a **plain** Spring context (`cucumber-spring`, no Boot), so it
> wires the bean explicitly in `TestConfig` rather than through auto-configuration —
> see below. The auto-configuration exists for real Spring Boot consumers of the client.

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

---

## Core concept 1 — the scenario context

Two glue beans, both `@Scope("cucumber-glue")`, make up the per-scenario memory:

| Bean | Responsibility |
|------|----------------|
| `ScenarioContextStore` | A `Map<String,Object>`. Steps `put`/`get` values by key. |
| `ScenarioContext` | Wraps the store with a JEXL engine; `resolveField("${...}")` evaluates expressions against the store. |

`cucumber-glue` scope means **cucumber-spring creates a fresh instance per scenario**,
so stored data never leaks between scenarios — isolation with zero cleanup code.

A typical step now drives the **REST client**, not a local service:

```java
@When("I create order {order} and store result in {word}")
public void createOrder(Order order, String resultKey) {
    Order created = orderApi.create(order);        // HTTP POST /orders
    scenarioStore().put(resultKey, created);        // remember the response
}
```

In the feature, the stored value is then addressable by its key:

```gherkin
When I create order ${order1} and store result in created
Then I expect that order ${created} has id assigned
When I read order with id ${created.id()} and store result in fetched
Then I expect that ${fetched} equals ${created}
```

---

## Core concept 2 — JEXL preprocessing (`${ ... }`)

Every data-table cell and inline `{param}` is passed through
`ScenarioContext.resolveField` before your step runs:

```
cell: ${created.customer().country()}
        │  matches ^\$\{(.+?)\}$ ?  yes
        │  strip ${ }            ->  "created.customer().country()"
        ▼
   JEXL evaluates it against a MapContext backed by ScenarioContextStore.getAll()
   → `created` is the stored object, `.customer().country()` are accessor calls
```

If a value is **not** wrapped in `${...}` it is left as a plain string (Jackson then
maps it to the target field type). Supported expression kinds:

| In a feature file | Resolves to |
|-------------------|-------------|
| `${order1}` | the entity stored under key `order1` |
| `${created.id()}` | accessor navigation on a stored entity |
| `${[`vip`,`priority`]}` | a JEXL array literal (backticks = JEXL strings) |
| `${[1,2,3]}` | a numeric array |
| `${now('-PT1H')}` | custom function call (see below) |
| `` ${`PL`} `` | a literal string |

### How a data-table row becomes a typed object

```
| cucId  | item   | quantity | price |
| order1 | widget | 3        | 9.99  |
```

1. `ParameterTypes.mapFields` runs `resolveField` on **every cell**.
2. `@JsonUnwrapped` in `BaseCucRecord<T>` splits `cucId` (the reference name) from the
   payload columns.
3. Jackson's `OBJECT_MAPPER` builds the payload record (`Order`) from the remaining
   columns.
4. A step stores the payload under its `cucId`, making it `${order1}`.

---

## Extending the infrastructure

### Add a JEXL function
Add a `public static` method to `CucJexlFunctions` — no other wiring needed:

```java
public static String upper(String s) { return s.toUpperCase(); }
```
Use it as `${upper(`abc`)}`.

### Add an endpoint
Keep the contract in lock-step across modules:

1. Add the method to `OrderApi` (client module) with explicitly-named parameters.
2. Mirror it in `OrderController` (service module).
3. Use it from a step via the autowired `OrderApi`.

### Accept a new entity type in tables / params
In `ParameterTypes`:

```java
// as a data-table row:
@DataTableType
public BaseCucRecord<MyDto> myDtoRecord(Map<String, String> row) {
    return convertToTypedRecord(row, new TypeReference<>() {});
}

// as an inline {myDto} parameter:
@ParameterType(value = ".*", name = "myDto")
public MyDto myDto(String s) {
    return scenarioContext.resolveField(s);
}
```
For editing to work (`updateRecordBuilderFields`), the DTO needs a builder whose
setter method names equal the field names. The api model records get this for free from
Lombok `@Builder(toBuilder = true)` + `@Jacksonized` (see `Order`/`Customer`), which
generate a field-named, prefix-less builder and wire Jackson to deserialize through it.

---

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
- **JEXL 3.3 sandbox.** JEXL 3.3 restricts method/class access by default, which blocks
  calls like `now(...)` and entity accessors. `ScenarioContext` lifts this with
  `JexlPermissions.UNRESTRICTED` — acceptable because test data is fully trusted.
- **Array literals are Java arrays.** `${[`a`,`b`]}` evaluates to `String[]`. Jackson
  converts it to a `List` when mapping a field; when comparing directly, normalise both
  sides to a `List` first (see `GeneralValidationSteps`).
- **Edit tables use a header row.** The "edit fields" table is horizontal:
  first row = field names, second row = values.
- **No Spring Boot in the tests.** A plain Spring `@Configuration` + `@ContextConfiguration`
  keeps the test context light while preserving the `cucumber-glue` scope.
