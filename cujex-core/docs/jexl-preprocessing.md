# JEXL preprocessing (`${ ... }`)

> Core concept 2 of 3 · [back to cujex-core](../README.md) · prev: [scenario context](scenario-context.md) · next: [JSON templates](json-templates.md)

Every data-table cell and inline `{param}` is passed through
`ScenarioContext.resolveField` **before** your step runs. Any value shaped like
`${ ... }` is evaluated as a [JEXL](https://commons.apache.org/proper/commons-jexl/)
expression against the [scenario context](scenario-context.md); anything else is left
as a plain string.

```
cell: ${created.customer().country()}
        │  matches ^\$\{(.+?)\}$ ?  yes
        │  strip ${ }            ->  "created.customer().country()"
        ▼
   JEXL evaluates it against a MapContext backed by ScenarioContextStore.getAll()
   → `created` is the stored object, `.customer().country()` are accessor calls
```

If a value is **not** wrapped in `${...}` it is left as a plain string (Jackson then
maps it to the target field type).

## Supported expression kinds

| In a feature file | Resolves to |
|-------------------|-------------|
| `${order1}` | the entity stored under key `order1` |
| `${created.id()}` | accessor navigation on a stored entity |
| `` ${[`vip`,`priority`]} `` | a JEXL array literal (backticks = JEXL strings) |
| `${[1,2,3]}` | a numeric array |
| `${now('-PT1H')}` | custom function call (see [Extending — custom functions](extending.md#add-a-jexl-function)) |
| `` ${`PL`} `` | a literal string |

## How a data-table row becomes a typed object

```
| cucId  | price |
| order1 | 9.99  |
```

1. `ParameterTypes.mapFields` runs `resolveField` on **every cell**.
2. `@JsonUnwrapped` in `BaseCucRecord<T>` splits `cucId` (the reference name) from the
   payload columns.
3. Jackson's `OBJECT_MAPPER` builds the payload record (e.g. `Order`) from the remaining
   columns.
4. A step stores the payload under its `cucId`, making it `${order1}`.

To accept your own domain type in tables and inline params, see
[Register a domain type](extending.md#register-a-domain-type-tables--params--the-ordertypes-pattern).

## Notes & gotchas

- **JEXL 3.3 sandbox.** JEXL 3.3 restricts method/class access by default, which blocks
  calls like `now(...)` and entity accessors. `ScenarioContext` lifts this with
  `JexlPermissions.UNRESTRICTED` — acceptable because test data is fully trusted.
- **Array literals are Java arrays.** `` ${[`a`,`b`]} `` evaluates to `String[]`. Jackson
  converts it to a `List` when mapping a field; when comparing directly, normalise both
  sides to a `List` first (see `GeneralValidationSteps`).
- **Edit tables use a header row.** The "edit fields" table is horizontal:
  first row = field names, second row = values.
