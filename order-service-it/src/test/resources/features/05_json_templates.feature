# Use case 5: building request models from shared JSON templates.
#
# Defining a whole request model in every scenario makes scenarios huge and hides
# which fields actually drive the behaviour under test. Storing one JSON file per
# case instead spawns a maze of fixtures. The middle ground shown here keeps a
# single "common case" stub (stubs/order_request.json) and, per scenario,
# overrides only the handful of fields that matter. Every override cell still
# flows through the same JEXL preprocessing as the rest of the suite.
@Templates
Feature: Building request models from JSON templates

  # A Background runs before EVERY scenario in this feature, so shared setup is
  # written once. Here it loads the common stub and seeds the variables both
  # scenarios lean on. Because the context store is recreated per scenario, this
  # setup is isolated too - nothing leaks between scenarios.
  Background:

    # Load the shared "success request" stub and remember it as template1.
    Given I load JSON stubs:
      | cucId     | file          |
      | template1 | order_request |

    # vipTags is a list override used below; stubCustomerName keeps the stub's
    # default customer name as a single ${...} token so an assertion can reference
    # it (a bare multi-word literal would not match the {word} parameter).
    And I define scenario variables:
      | vipTags          | ${[`vip`,`priority`]} |
      | stubCustomerName | Default Customer      |

  Scenario: Override only the fields that matter

    # Start from the stub and change only the first line item (its sku and
    # quantity) plus tags. Everything else - price, status, the other line item,
    # the whole customer block - is inherited, so the scenario stays focused on
    # exactly the fields it exercises. The indexed column items[0].quantity shows
    # how an override propagates into a nested collection element.
    Given I create models from template template1:
      | cucId  | items[0].sku | items[0].quantity | tags       |
      | order1 | console      | 2                 | ${vipTags} |

    When I create order ${order1} and store result in created

    # Overridden fields took effect...
    Then I expect that ${created.items().get(0).sku()} equals ${`console`}
    And I expect that ${created.items().get(0).quantity()} equals ${2}
    And I expect that ${created.tags()} contains only ${vipTags}

    # ...while untouched fields came straight from the stub.
    And I expect that order ${created} has status NEW
    And I expect that ${created.customer().country()} equals ${`US`}

  Scenario: Build a list override inline, without a pre-defined variable

    # The previous scenario fed tags from a named variable (${vipTags}). Here the
    # whole list is written straight into the cell as a JEXL array literal, so the
    # override is self-contained - handy for one-off values you won't reuse.
    # Backticks make each element a JEXL string.
    Given I create models from template template1:
      | cucId  | items[0].sku | tags                          |
      | order1 | clearance    | ${[`last-call`,`final-sale`]} |

    When I create order ${order1} and store result in created

    # The inline list landed as-is...
    Then I expect that ${created.tags()} contains only ${[`last-call`,`final-sale`]}

    # ...and the untouched first line item quantity still came from the stub (1).
    And I expect that ${created.items().get(0).quantity()} equals ${1}

  Scenario: Reuse one template for several models and patch nested fields

    # One template, two models. A blank cell means "inherit from the stub"; a
    # dotted column (customer.country) patches a nested field without restating
    # the rest of the customer block.
    Given I create models from template template1:
      | cucId  | items[0].sku | items[0].quantity | customer.country |
      | order1 | console      | 2                 |                  |
      | order2 | laptop       | 1                 | DE               |

    # order1 left customer.country blank -> inherited "US" from the stub.
    Then I expect that ${order1.customer().country()} equals ${`US`}

    # order2 patched only the country; the nested name is still the stub default.
    And I expect that ${order2.customer().country()} equals ${`DE`}
    And I expect that ${order2.customer().name()} equals ${stubCustomerName}

    # Both are complete, valid orders the service accepts.
    When I create order ${order2} and store result in created
    Then I expect that order ${created} has id assigned

  Scenario: Set fields to explicit null or empty string

    # A blank cell inherits the stub value, so to *clear* an inherited field you
    # need an explicit marker. (NULL) writes a JSON null and "" writes an empty
    # string - both differ from "leave it alone".
    Given I create models from template template1:
      | cucId  | customer.name | customer.country |
      | order1 | ""            | (NULL)           |

    # "" overrode the stub's "Default Customer" with an empty string.
    Then I expect that ${order1.customer().name()} equals ${``}

    # (NULL) cleared the stub's "US" to a real null.
    And I expect that ${order1.customer().country()} equals ${null}

  Scenario: Decompose a nested collection into one row per element

    # Full-path headers (items[0].sku, items[0].quantity, items[1].sku, ...) get
    # unwieldy fast. Instead, open a collection scope with an items[] column and
    # give each element its own row: the cell under items[] is the selector that
    # fills the brackets, and the .sku / .quantity columns are relative to it. The
    # model row (the one with a cucId) carries the first element; blank-cucId rows
    # add more. Because elements are rows, the two orders below can - unlike a
    # single wide header - hold a different number of line items.
    Given I create models from template template1:
      | cucId  | items[] | .sku    | .quantity |
      | order1 | [0]     | console | 2         |
      |        | [1]     | mouse   | 1         |
      | order2 | [0]     | laptop  | 5         |

    # order1 replaced both stub elements with two of its own.
    Then I expect that ${order1.items().get(0).sku()} equals ${`console`}
    And I expect that ${order1.items().get(1).sku()} equals ${`mouse`}

    # order2 defined only element 0; element 1 (gadget) still came from the stub.
    And I expect that ${order2.items().get(0).sku()} equals ${`laptop`}
    And I expect that ${order2.items().get(0).quantity()} equals ${5}
    And I expect that ${order2.items().get(1).sku()} equals ${`gadget`}

  Scenario: Mix object scopes, append, and a record matcher in one table

    # One table, several kinds of column. customer: opens an object scope, so .name
    # and .country patch the nested customer without restating the block. items[]
    # opens the collection scope: [+] appends a brand-new element, and [sku=widget]
    # finds the stub element that already has that sku and patches it in place
    # (appending one only if nothing matched).
    Given I create models from template template1:
      | cucId  | customer: | .name | .country | items[]      | .sku  | .quantity |
      | order1 |           | Ada   | GB       | [+]          | cable | 7         |
      |        |           |       |          | [sku=widget] |       | 42        |

    When I create order ${order1} and store result in created

    # The object scope patched the nested customer.
    Then I expect that ${created.customer().name()} equals ${`Ada`}
    And I expect that ${created.customer().country()} equals ${`GB`}

    # The stub's two elements stayed, [+] appended a third, and the matcher bumped
    # the widget element's quantity in place.
    And I expect that ${created.items().size()} equals ${3}
    And I expect that ${created.items().get(0).sku()} equals ${`widget`}
    And I expect that ${created.items().get(0).quantity()} equals ${42}
    And I expect that ${created.items().get(2).sku()} equals ${`cable`}
    And I expect that ${created.items().get(2).quantity()} equals ${7}

  Scenario: Decompose collections nested inside collection elements

    # The real win of decomposition shows up with a collection *inside* a
    # collection's elements: here each line item is a bundle of components. The
    # full-path form (items[0].components[0].name, items[0].components[1].name,
    # items[1].components[0].name, ...) is unreadable. Instead items[] opens the
    # outer scope and .components[] opens a scope nested inside the current item,
    # so the relative .name / .quantity columns land on items[<i>].components[<j>].
    #
    # Columns are read left-to-right, so an item's own fields (.sku) come before
    # its .components[] opener. A new selector under items[] starts a new item; a
    # blank items[] cell simply continues the current one, so the second component
    # row needs no item selector at all.
    Given I create models from template template1:
      | cucId  | items[] | .sku    | .components[] | .name   | .quantity |
      | order1 | [0]     | laptop  | [0]           | cpu     | 1         |
      |        |         |         | [1]           | ram     | 2         |
      |        | [1]     | mouse   | [0]           | sensor  | 1         |
      | order2 | [0]     | desk    | [+]           | drawer  | 3         |

    # order1: two line items whose component lists differ in size (2 vs 1).
    Then I expect that ${order1.items().size()} equals ${2}
    And I expect that ${order1.items().get(0).components().size()} equals ${2}
    And I expect that ${order1.items().get(0).components().get(0).name()} equals ${`cpu`}
    And I expect that ${order1.items().get(0).components().get(1).name()} equals ${`ram`}
    And I expect that ${order1.items().get(0).components().get(1).quantity()} equals ${2}
    And I expect that ${order1.items().get(1).components().size()} equals ${1}
    And I expect that ${order1.items().get(1).components().get(0).name()} equals ${`sensor`}

    # order2: a single item with a single component, appended with [+].
    And I expect that ${order2.items().get(0).sku()} equals ${`desk`}
    And I expect that ${order2.items().get(0).components().size()} equals ${1}
    And I expect that ${order2.items().get(0).components().get(0).quantity()} equals ${3}

    # The whole nested structure survives a real round-trip through the service.
    When I create order ${order1} and store result in created
    Then I expect that ${created.items().get(0).components().get(1).name()} equals ${`ram`}

  Scenario: Validate decomposed models with a table instead of many equals steps

    # The same decomposition grammar reads just as well as it writes. Instead of a
    # wall of "I expect that ${order1.items()...} equals ..." steps, the validation
    # table addresses the stored model with the identical columns used to build it:
    # items[] opens the outer scope, .components[] the nested one, and the relative
    # .name / .quantity cells become *expected* values asserted against order1.
    Given I create models from template template1:
      | cucId  | items[] | .sku    | .components[] | .name   | .quantity |
      | order1 | [0]     | laptop  | [0]           | cpu     | 1         |
      |        |         |         | [1]           | ram     | 2         |
      |        | [1]     | mouse   | [0]           | sensor  | 1         |

    # Assertions are a subset by default: unlisted fields and extra elements are
    # ignored. Opt into a size check with a '#' column - a plain items# pins the
    # outer list, a relative .components# pins the list inside the current item.
    Then I expect models match:
      | cucId  | items# | items[]  | .sku    | .components# | .components[] | .name  | .quantity |
      | order1 | 2      | [0]      | laptop  | 2            | [0]           | cpu    | 1         |
      |        |        |          |         |              | [1]           | ram    | 2         |
      |        |        | [1]      | mouse   | 1            | [0]           | sensor | 1         |

    # Selectors also match by field value, so order independence is easy to assert.
    And I expect models match:
      | cucId  | items[]      | .sku   | .components[]   | .quantity |
      | order1 | [sku=mouse]  | mouse  | [name=sensor]   | 1         |
