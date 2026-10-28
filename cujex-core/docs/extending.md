# Extending Cujex

> [back to cujex-core](../README.md) · see also: [scenario context](scenario-context.md) · [JEXL preprocessing](jexl-preprocessing.md) · [JSON templates](json-templates.md)

`cujex-core` is domain-agnostic: it ships the context store, JEXL engine, template
loader and generic steps, but knows nothing about orders. A new suite consumes it and
adds only its own domain glue. The example module (`order-service-it`) **is** such an
extension — use it as the template.

## Wiring a new suite onto `cujex-core`

1. **Depend on it** (test scope):
   ```xml
   <dependency>
       <groupId>io.github.dominys</groupId>
       <artifactId>cujex-core</artifactId>
       <scope>test</scope>
   </dependency>
   ```
2. **Import the Spring config** from your `@CucumberContextConfiguration` context so the
   context store, JEXL functions and generic steps get picked up (see `TestConfig`):
   ```java
   @Import(CujexCoreConfig.class)
   ```
3. **Add both glue packages to the Cucumber glue path** — your own plus the framework's
   (see `RunCucumberIT`):
   ```java
   @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.acme.it, io.github.dominys.cujex")
   ```

## Register a domain type (tables / params) — the `OrderTypes` pattern

Write a small glue class that autowires `ScenarioContext` and reuses the static
`ParameterTypes.toRecord(...)` / `resolveRow(...)` helpers. **Do not extend
`ParameterTypes`** — Cucumber would double-register the inherited `@DataTableType` /
`@DefaultParameterTransformer` methods and fail. Keep them as independent glue classes
(`OrderTypes` is the canonical example):

```java
public class OrderTypes {

    private final ScenarioContext scenarioContext;

    @Autowired
    public OrderTypes(ScenarioContext scenarioContext) {
        this.scenarioContext = scenarioContext;
    }

    // inline {myDto} parameter:
    @ParameterType(value = ".*", name = "myDto")
    public MyDto myDto(String ref) {
        return scenarioContext.resolveField(ref);
    }

    // data-table row -> typed record:
    @DataTableType
    public BaseCucRecord<MyDto> myDtoRecord(Map<String, String> row) {
        return ParameterTypes.toRecord(row, scenarioContext, new TypeReference<>() {});
    }
}
```

For field editing (`EditFieldsRecord` / the edit steps) to work, the DTO needs a builder
whose setter method names equal the field names. The api model records get this for free
from Lombok `@Builder(toBuilder = true)` + `@Jacksonized` (see `Order`/`Customer`).

## Add a domain step

Extend `io.github.dominys.cujex.steps.BaseSteps` (gives you the autowired
`ScenarioContext` + `ScenarioContextStore`) and add your `@Given`/`@When`/`@Then`
methods — `OrderSteps` is the example driving the REST client.

## Add a JEXL function

There are four ways to expose functions to `${...}` expressions, in increasing
decoupling from the framework.

**a) A built-in (framework-owned).** Add a `public static` method to
`CucJexlFunctions` in `cujex-core`:

```java
public static String upper(String s) { return s.toUpperCase(); }
```
Use it as `` ${upper(`abc`)} ``.

**b) Extra functions in the default namespace (consumer-owned).** Your suite can
contribute functions callable *without* a prefix — next to the built-ins — by
subclassing `CucJexlFunctions` (to keep `now()`/`uuid()`) and implementing
`CujexJexlFunctions` with a blank namespace. See `OrderJexlFunctions`:

```java
public class OrderJexlFunctions extends CucJexlFunctions implements CujexJexlFunctions {
    public String namespace() { return ""; }   // default namespace
    public Object functions() { return this; }
    public int taxed(int amount, int percent) { return amount + amount * percent / 100; }
}
```
Register it as a `@Bean` (see `TestConfig`) and use it as `${taxed(100, 20)}`.

**c) A custom namespace (consumer-owned).** Return a non-blank `namespace()` to
expose functions under a prefix, leaving the default namespace untouched. See
`MathJexlFunctions`:

```java
public class MathJexlFunctions implements CujexJexlFunctions {
    public String namespace() { return "math"; } // callable as ${math:...}
    public Object functions() { return this; }
    public int max(int a, int b) { return Math.max(a, b); }
}
```
Register it as a `@Bean` and use it as `${math:max(3, 7)}`. `ScenarioContext`
discovers every `CujexJexlFunctions` bean automatically via `ObjectProvider`;
`functions()` may return `this` (instance methods) or a `Class` (static methods).

**d) A context-aware function (consumer-owned).** To let a function read the
scenario context, return your own `Class` from `functions()` and add a constructor
that takes the `CujexJexlContext`. JEXL then builds a fresh functor per evaluation
and injects the live context, from which you can reach the whole `ScenarioContext`
(e.g. to resolve another expression). See `ContextAwareJexlFunctions`:

```java
public class ContextAwareJexlFunctions implements CujexJexlFunctions {
    private final CujexJexlContext context;
    public ContextAwareJexlFunctions() { this.context = null; }              // Spring descriptor
    public ContextAwareJexlFunctions(CujexJexlContext ctx) { this.context = ctx; } // JEXL functor
    public String namespace() { return "ctx"; }
    public Object functions() { return ContextAwareJexlFunctions.class; }    // a Class -> context injected
    public Object ref(String expr) {
        return context.scenarioContext().resolveField("${" + expr + "}");
    }
}
```
Register it as a `@Bean` and use it as `${ctx:ref('created.id()')}`. Because a new
functor is created per evaluation against the current context, this is thread-safe
and always sees the scenario's latest state. Resolution of `I define scenario
variables:` is write-through — each row is stored before the next is resolved — so a
later cell can reference an earlier one in the *same* table, directly (`${limit}`) or
through a context-aware function (`${ctx:ref('limit')}`).
`06_custom_jexl_functions.feature` exercises (b), (c) and (d).

## Add an endpoint

Keep the contract in lock-step across modules:

1. Add the method to `OrderApi` (client module) with explicitly-named parameters.
2. Mirror it in `OrderController` (service module).
3. Use it from a step via the autowired `OrderApi`.

## Messaging over Kafka

Asynchronous messaging is a separate opt-in extension — see
[`cujex-kafka`](../../cujex-kafka/README.md).
