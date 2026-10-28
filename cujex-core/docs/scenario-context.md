# Scenario context storage

> Core concept 1 of 3 · [back to cujex-core](../README.md) · next: [JEXL preprocessing](jexl-preprocessing.md)

The scenario context is Cujex's per-scenario **memory**: steps store their results
under a key and reference them again later, in the same scenario, from Java **or** from
a feature file.

## The two beans

Two glue beans, both `@Scope("cucumber-glue")`, make up the per-scenario memory:

| Bean | Responsibility |
|------|----------------|
| `ScenarioContextStore` | A `Map<String,Object>`. Steps `put`/`get` values by key. |
| `ScenarioContext` | Wraps the store with a JEXL engine; `resolveField("${...}")` evaluates expressions against the store. |

`cucumber-glue` scope means **cucumber-spring creates a fresh instance per scenario**,
so stored data never leaks between scenarios — isolation with zero cleanup code.

## Storing and reading back

A typical step stores whatever it produced under a key:

```java
@When("I create order {order} and store result in {word}")
public void createOrder(Order order, String resultKey) {
    Order created = orderApi.create(order);        // whatever your step does
    scenarioStore().put(resultKey, created);        // remember the result
}
```

In the feature, the stored value is then addressable by its key — and navigable with
[JEXL](jexl-preprocessing.md):

```gherkin
When I create order ${order1} and store result in created
Then I expect that order ${created} has id assigned
When I read order with id ${created.id()} and store result in fetched
Then I expect that ${fetched} equals ${created}
```

`scenarioStore()` and `scenarioContext()` are provided to every step that extends
`BaseSteps` — see [Extending Cujex](extending.md#add-a-domain-step).
