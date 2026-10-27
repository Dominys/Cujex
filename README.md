# it-tests-example

A small, self-contained example of how to write **e2e / service-level tests** with
**Cucumber** and a **JEXL** preprocessing layer. The infrastructure here is extracted
and decoupled from a real project (`banner-service-integration-tests`) so it can serve
as documented, runnable reference material.

The two patterns it is built to teach:

1. **Scenario context storage** – steps store their results in a per-scenario
   key/value store and reference them later.
2. **JEXL preprocessing** – every feature-file value shaped like `${ ... }` is
   evaluated as an expression *before* it reaches your step code.

> There is no external infrastructure. The "system under test" is a tiny in-memory
> `OrderService`. Run everything with `./mvnw test`.

---

## Quick start

```bash
./mvnw test
```

Reads every `*.feature` under `src/test/resources/features`, runs them through the
glue in `org.dominys.ittests`, and writes an HTML report to `target/cucumber.html`.

Requirements: JDK 21. Maven is provided via the wrapper (`./mvnw`).

---

## Project layout

```
src/main/java/org/dominys/ittests/sut/      # Sample System Under Test (domain + service)
  Order.java, Customer.java, OrderStatus.java, OrderService.java
                                             # DTOs are Java records + Lombok @Builder/@Jacksonized

src/test/java/org/dominys/ittests/
  RunCucumberTest.java                       # JUnit Platform suite (entry point)
  infra/                                     # The reusable Cucumber + JEXL infrastructure
    ScenarioContextStore.java                #   per-scenario key/value store
    ScenarioContext.java                     #   JEXL engine: ${...} resolution + field editing
    CucJexlFunctions.java                    #   custom JEXL functions (now(), uuid(), ...)
    ParameterTypes.java                      #   ObjectMapper + @ParameterType/@DataTableType
    BaseCucRecord.java, EditFieldsRecord.java, StepVariables.java
    CucConstants.java, CucIdsToListDeserializer.java
    TestConfig.java                          #   lightweight Spring context (no Boot)
    CucumberSpringConfiguration.java         #   @CucumberContextConfiguration
  steps/
    BaseSteps.java                           #   access to context + store
    InfraSteps.java                          #   "I define scenario variables:"
    GeneralValidationSteps.java              #   generic assertions
    OrderSteps.java                          #   domain steps driving OrderService

src/test/resources/features/
  01_context_storage.feature                 # store / reference / read-back loop
  02_jexl_preprocessing.feature              # functions, literals, nested navigation
  03_field_editing.feature                   # edit selected fields of a stored entity
  04_end_to_end.feature                      # capstone tying it all together
```

---

## Core concept 1 — the scenario context

Two glue beans, both `@Scope("cucumber-glue")`, make up the per-scenario memory:

| Bean | Responsibility |
|------|----------------|
| `ScenarioContextStore` | A `Map<String,Object>`. Steps `put`/`get` values by key. |
| `ScenarioContext` | Wraps the store with a JEXL engine; `resolveField("${...}")` evaluates expressions against the store. |

`cucumber-glue` scope means **cucumber-spring creates a fresh instance per scenario**,
so stored data never leaks between scenarios — isolation with zero cleanup code.

A typical step:

```java
@When("I create order {order} and store result in {word}")
public void createOrder(Order order, String resultKey) {
    Order created = orderService.create(order);   // call the service
    scenarioStore().put(resultKey, created);       // remember the response
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
setter method names equal the field names. The sample DTOs get this for free from
Lombok `@Builder(toBuilder = true)` + `@Jacksonized` (see `Order`/`Customer`), which
generate a field-named, prefix-less builder and wire Jackson to deserialize through it.

---

## Notes & gotchas

- **JEXL 3.3 sandbox.** JEXL 3.3 restricts method/class access by default, which blocks
  calls like `now(...)` and entity accessors. `ScenarioContext` lifts this with
  `JexlPermissions.UNRESTRICTED` — acceptable because test data is fully trusted.
- **Array literals are Java arrays.** `${[`a`,`b`]}` evaluates to `String[]`. Jackson
  converts it to a `List` when mapping a field; when comparing directly, normalise both
  sides to a `List` first (see `GeneralValidationSteps`).
- **Edit tables use a header row.** The "edit fields" table is horizontal:
  first row = field names, second row = values.
- **No Spring Boot.** A plain Spring `@Configuration` + `@ContextConfiguration` keeps the
  example light while preserving the `cucumber-glue` scope.
