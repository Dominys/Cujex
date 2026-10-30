# Use case 3: field-level editing of a stored entity.
#
# Sometimes you want "the same order as before, but with these two fields changed".
# The edit step does exactly that: it round-trips the stored entity through JSON,
# overwriting only the named columns with their JEXL-resolved values. It works for any
# Jackson-mappable model (plain record, POJO, or Lombok @Builder type).
@Editing
Feature: Editing selected fields of a stored entity

  Scenario: Clone an order while overriding only some fields

    Given I prepare orders:
      | cucId  | price | tags                  |
      | order1 | 9.99  | ${[`vip`,`priority`]} |

    # Take ${order1}, override only `price`, store as `order2`.
    # (An edit table is read with a header row: first row = field names, second = values.)
    When I edit ${order1} fields and store result in order2
      | price |
      | 49.95 |

    # Untouched field is carried over from the original...
    Then I expect that ${order2.tags()} equals ${order1.tags()}

    # ...while the overridden field holds the new value.
    And I expect that ${order2.price()} equals ${49.95}

    # The edited clone can then be sent to the service like any other input.
    When I create order ${order2} and store result in created
    Then I expect that order ${created} has id assigned
