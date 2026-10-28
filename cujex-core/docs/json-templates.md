# JSON templates (request/response stubs)

> Core concept 3 of 3 · [back to cujex-core](../README.md) · prev: [JEXL preprocessing](jexl-preprocessing.md)

Building a whole request model inline in every scenario makes scenarios huge and hides
**which fields actually drive the flow under test**. Moving every model into its own
resource file swings too far the other way — a sprawl of near-identical JSON. Cujex uses
a **mixed approach**: keep a small set of common "stub" JSON files (e.g. a success
request), load one, and override only the fields the scenario cares about.

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

## How it works

1. **Stubs are self-describing.** Each file under `src/test/resources/stubs` carries a
   small `@descriptor` section naming the model type it builds, so there is no central
   catalogue to maintain. Add a file, declare its type, and it is ready to load:

   ```json
   {
     "@descriptor": { "type": "io.github.dominys.cujex.examples.api.model.Order" },
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

## Cell conventions for overrides

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

## Decomposing nested collections and objects

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

### Collections nested inside collection elements

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

## Validating models with a table (`I expect models match:`)

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

## Adding a new stub

1. Drop the JSON under `src/test/resources/stubs/`.
2. Give it an `@descriptor` section declaring the model type:
   `{ "@descriptor": { "type": "io.github.dominys.cujex.examples.api.model.Order" }, ... }`.

No Java or glue changes needed — the two steps are type-agnostic and the loader reads
everything it needs from the file.
