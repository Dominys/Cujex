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

It also demonstrates a third, closely related pattern:

3. **JSON templates** – load a common request/response stub and override only the
   fields a scenario actually exercises (see *Core concept 3* below).

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

This builds all four modules, packages the service into a Docker image, starts it
in a container, and runs the five feature files through the glue in
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

### Running from IntelliJ IDEA (macOS + Rancher Desktop / Colima)

IDEA launches tests with its own environment, not your shell's, so the same socket
problem shows up as:

```
Could not find a valid Docker environment. Please see logs and check configuration
```

These are **environment variables**, not JVM `-D` arguments. Add them to the run
configuration (**Run → Edit Configurations… → your JUnit/Cucumber config →
Environment variables**), one per row:

| Name | Value (Rancher Desktop) | Value (Colima) |
|------|-------------------------|----------------|
| `DOCKER_HOST` | `unix:///Users/<you>/.rd/docker.sock` | `unix:///Users/<you>/.colima/default/docker.sock` |
| `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` | `/var/run/docker.sock` | `/var/run/docker.sock` |
| `TESTCONTAINERS_RYUK_DISABLED` | `true` | `true` |

- `DOCKER_HOST` points Testcontainers at the real socket; `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`
  is the path mounted **into** containers (Ryuk, the client); disabling Ryuk avoids its
  own socket probing.
- To avoid retyping, edit the **template** (the greyed-out *JUnit* / *Cucumber Java*
  entry under *Edit Configurations…*) so every new run inherits these values.
- Alternatively, launch IDEA from a terminal that already `export`ed the variables above
  (e.g. `open -a "IntelliJ IDEA"` from that shell) and it will inherit them.

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
    template/                                   # concept #3: JSON request/response stubs
      JsonTemplate.java                         #   self-loading stub (reads @descriptor) + overrides
      JsonStubRef.java                          #   row record for the "load JSON stubs" table
    steps/
      BaseSteps.java                            #   access to context + store
      InfraSteps.java                           #   "I define scenario variables:"
      GeneralValidationSteps.java               #   generic assertions
      ModelValidationSteps.java                 #   "I expect models match:" table assertions
      OrderSteps.java                           #   domain steps driving the REST client
      TemplateSteps.java                        #   "load JSON stubs" / "create models from template"
  src/test/resources/features/
    01_context_storage.feature                  # store / reference / read-back loop
    02_jexl_preprocessing.feature               # functions, literals, nested navigation
    03_field_editing.feature                    # edit selected fields of a stored entity
    04_end_to_end.feature                       # capstone tying it all together
    05_json_templates.feature                   # load a stub, override only relevant fields
  src/test/resources/stubs/
    order_request.json                          # shared "success request" stub
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
| cucId  | price |
| order1 | 9.99  |
```

1. `ParameterTypes.mapFields` runs `resolveField` on **every cell**.
2. `@JsonUnwrapped` in `BaseCucRecord<T>` splits `cucId` (the reference name) from the
   payload columns.
3. Jackson's `OBJECT_MAPPER` builds the payload record (`Order`) from the remaining
   columns.
4. A step stores the payload under its `cucId`, making it `${order1}`.

---

## Core concept 3 — JSON templates (request/response stubs)

Building a whole request model inline in every scenario makes scenarios huge and hides
**which fields actually drive the flow under test**. Moving every model into its own
resource file swings too far the other way — a sprawl of near-identical JSON. This
example uses a **mixed approach**: keep a small set of common "stub" JSON files (e.g. a
success request), load one, and override only the fields the scenario cares about.

```gherkin
@Templates
Feature: Building request models from JSON templates

  # Shared setup runs once, before every scenario.
  Background:
    Given I load JSON stubs:
      | cucId     | file          |
      | template1 | order_request |

  Scenario: Override only the fields that matter
    Given I create models from template template1:
      | cucId  | items[0].sku | items[0].quantity |
      | order1 | console      | 2                 |
    When I create order ${order1} and store result in created
    Then I expect that ${created.items().get(0).sku()} equals ${`console`}
    And  I expect that order ${created} has status NEW   # inherited from the stub
```

Loading the stub is identical for every scenario, so it lives in a **`Background`**
block — Gherkin's built-in "before each scenario" hook. The per-scenario
`I create models from template ...` step is what varies, and that is all each scenario
has to spell out.

### How it works

1. **Stubs are self-describing.** Each file under `src/test/resources/stubs` carries a
   small `@descriptor` section naming the model type it builds, so there is no central
   catalogue to maintain. Add a file, declare its type, and it is ready to load:

   ```json
   {
     "@descriptor": { "type": "org.dominys.ittests.api.model.Order" },
     "price": 9.99,
     "items": [ { "sku": "widget", "quantity": 1, "price": 9.99 } ],
     "customer": { "name": "Default Customer", "country": "US" }
   }
   ```
   Because the type lives in the file, two stubs can target the **same** model
   (`order_request.json`, `order_bulk_request.json`, ...) with zero extra wiring.

2. **`I load JSON stubs:`** takes rows of `(cucId, file)`. `JsonTemplate.load(file)`
   reads `/stubs/{file}.json`, pulls the type from `@descriptor`, strips that meta
   section out, and stores the resulting `JsonTemplate` under `cucId` (`template1`).

3. **`I create models from template {word}:`** reads the referenced template, then for
   each row deep-copies the base JSON and applies the row's columns as overrides before
   deserializing into the target type. The built model is stored under its `cucId`
   (`order1`), ready to be used as `${order1}`.

### Cell conventions for overrides

| Cell value | Effect on that field |
|------------|----------------------|
| *(blank)* | inherit the stub's value (the common case) |
| `(NULL)` | set the field to an explicit JSON `null` |
| `""` | set the field to an explicit empty string `""` |
| dotted key, e.g. `customer.country` | override a **nested** field |
| indexed key, e.g. `items[0].quantity` | override an element of a **nested array** |
| anything else | run through JEXL (`resolveField`) just like a normal table cell |

A blank cell means "leave it alone", so clearing an inherited field needs an explicit
marker — `(NULL)` for `null`, `""` for an empty string:

```gherkin
Given I create models from template template1:
  | cucId  | customer.name | customer.country |
  | order1 | ""            | (NULL)           |   # name -> "", country -> null
```


Because overrides are keyed by **column name**, a nested field is addressed with a dotted
path (`customer.country`) and an array element with an indexed one (`items[0].quantity`);
`JsonTemplate.setPath` walks/creates the intermediate objects, arrays and elements.
One template can spawn several models in a single table — each row patches a different
slice:

```gherkin
Given I create models from template template1:
  | cucId  | items[0].sku | items[0].quantity | customer.country |
  | order1 | console      | 2                 |                  |   # country inherits "US"
  | order2 | laptop       | 1                 | DE               |   # country overridden, name inherited
```

### Decomposing nested collections and objects

Full paths work, but real models nest deeply, so repeating `items[0].`, `items[1].`,
`customer.` in every header makes tables wide and hard to read. Headers can instead be
written in a **decomposed** form that opens a scope once and keeps the following columns
short:

| Header form | Meaning |
|-------------|---------|
| `customer:` | open an **object scope** — the following `.field` columns are relative to `customer` |
| `items[]` | open a **collection scope** — the cell under it is a *selector* that fills the brackets |
| `.components[]` | a relative column ending in `[]` opens a collection scope **nested inside the current scope** |
| `.name`, `.quantity` | **relative** column, appended to the currently open scope (`customer.name`, `items[0].quantity`) |
| `price`, `customer.country` | a plain header is still a full **root path** and resets the scope |

A collection cell holds a selector in brackets:

| Selector | Effect |
|----------|--------|
| `[0]` | patch element 0 |
| `[+]` | append a brand-new element |
| `[sku=widget]` | patch the element whose `sku` equals `widget`, appending one if nothing matches |
| *(blank)* | stay on the element this column resolved on the previous row — a continuation row |

Elements are **rows**, not columns: the row that carries a `cucId` starts a new model and
holds its first element; a row with a **blank** `cucId` keeps adding elements to the model
above it. Because of that, one table can give each model a *different* number of elements —
impossible with a fixed set of `items[0]…items[n]` headers:

```gherkin
Given I create models from template template1:
  | cucId  | items[] | .sku    | .quantity |
  | order1 | [0]     | console | 2         |   # order1 has two items…
  |        | [1]     | mouse   | 1         |   #   (extra element on a blank-cucId row)
  | order2 | [0]     | laptop  | 5         |   # …order2 has one
```

Object and collection scopes can appear side by side in the same table. Here `customer:`
patches the nested customer, `[+]` appends an item, and `[sku=widget]` edits the stub's
existing widget in place:

```gherkin
Given I create models from template template1:
  | cucId  | customer: | .name | .country | items[]      | .sku  | .quantity |
  | order1 |           | Ada   | GB       | [+]          | cable | 7         |
  |        |           |       |          | [sku=widget] |       | 42        |
```

`TemplateSteps` resolves each selector to a concrete `items[N]` index once per row (seeding
an append cursor from the stub's current element count), so both `.sku` and `.quantity` on a
row land on the *same* element, and `JsonTemplate.setPath` then walks/creates the path.

#### Collections nested inside collection elements

Decomposition pays off most when a collection lives *inside* another collection's elements —
here each line item carries its own `components` list. A `.components[]` header (a relative
column that itself ends in `[]`) opens a collection scope **nested inside the current item**,
so the `.name` / `.quantity` columns after it resolve against `items[<i>].components[<j>]`:

```gherkin
Given I create models from template template1:
  | cucId  | items[] | .sku   | .components[] | .name  | .quantity |
  | order1 | [0]     | laptop | [0]           | cpu    | 1         |
  |        |         |        | [1]           | ram    | 2         |   # same item, 2nd component
  |        | [1]     | mouse  | [0]           | sensor | 1         |   # next item, its 1st component
  | order2 | [0]     | desk   | [+]           | drawer | 3         |
```

Two rules make this readable:

* **Columns are positional** — an item's own fields (`.sku`) must come *before* its
  `.components[]` opener, because the scope is `items[0]` up to the opener and
  `items[0].components[<j>]` after it.
* **A blank collection cell continues the current element** — a non-blank selector under
  `items[]` *starts* a new item, while a blank cell re-enters the item from the row above, so
  the second component row needs no item selector. Each element keeps its own append cursor
  (keyed by the full `items[0].components` path), so order1's two items end up with component
  lists of *different* sizes (2 and 1) in a single table.

### Validating models with a table (`I expect models match:`)

The same decomposition grammar reads as well as it writes. A cluster of
`I expect that ${order1.items()...} equals ...` steps can collapse into one
`Then I expect models match:` table whose columns address a **stored** model exactly like
the creation table — only now each `.field` cell is an **expected** value, not an override:

```gherkin
Then I expect models match:
  | cucId  | items# | items[] | .sku   | .components# | .components[] | .name  | .quantity |
  | order1 | 2      | [0]     | laptop | 2            | [0]           | cpu    | 1         |
  |        |        |         |        |              | [1]           | ram    | 2         |
  |        |        | [1]     | mouse  | 1            | [0]           | sensor | 1         |
```

The row/scope rules are identical to creation: the `cucId` names the stored root (fetched
from the scenario store), `items[]` / `.components[]` open (outer / nested) collection
scopes, a blank collection cell continues the element from the row above, and `.field`
columns are relative to the active scope. Cells are placeholder- and JEXL-aware (`(NULL)`,
`""`, `${ ... }`) and each one is compared against the actual value **using its runtime
type**, so the string cell `1` matches an `Integer`, `9.99` a `Double`, and so on.

Two things differ from the creation table:

* **Subset by default** — only the cells you list are asserted; unlisted fields and extra
  collection elements are ignored. Collection **sizes are therefore not pinned** unless you
  ask. Opt in with a `#` column: a plain `items#` pins the outer list, a relative
  `.components#` (placed after its `items[]` opener so the scope is set) pins the list inside
  the current item. The cell is the expected count.
* **Addressing-only selectors** — `[n]`, a blank cell (continue), and `[field=value]` (the
  first element whose `field` equals `value`) locate an element to assert. `[+]` has no
  meaning when reading and is rejected. Matchers make order-independent assertions easy:

```gherkin
And I expect models match:
  | cucId  | items[]     | .sku  | .components[] | .quantity |
  | order1 | [sku=mouse] | mouse | [name=sensor] | 1         |
```

`ModelValidationSteps` navigates the stored model reflectively (record accessors like
`items()` / `name()` plus list `get(index)`), resolving each selector against the *actual*
list, then compares with `usingRecursiveComparison()` after coercing the expected cell to the
actual field's type.

### Adding a new stub

1. Drop the JSON under `src/test/resources/stubs/`.
2. Give it an `@descriptor` section declaring the model type:
   `{ "@descriptor": { "type": "org.dominys.ittests.api.model.Order" }, ... }`.

No Java or glue changes needed — the two steps are type-agnostic and the loader reads
everything it needs from the file.

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
