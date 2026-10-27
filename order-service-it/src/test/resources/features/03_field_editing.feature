# Use case 3: field-level editing of a stored entity.
#
# Sometimes you want "the same order as before, but with these two fields changed".
# updateRecordBuilderFields does exactly that: it reflectively calls the builder
# method whose name equals each column, using the JEXL-resolved value.
@Editing
Feature: Editing selected fields of a stored entity

  Scenario: Clone an order while overriding only some fields

    Given I prepare orders:
      | cucId  | item   | quantity | price |
      | order1 | widget | 1        | 9.99  |

    # Take ${order1}, override only `quantity` and `price`, store as `order2`.
    # (An edit table is read with a header row: first row = field names, second = values.)
    When I edit order ${order1} fields and store result in order2
      | quantity | price |
      | 5        | 49.95 |

    # Untouched field is carried over from the original...
    Then I expect that ${order2.item()} equals ${order1.item()}

    # ...while the overridden fields hold the new values.
    And I expect that ${order2.quantity()} equals ${5}

    # The edited clone can then be sent to the service like any other input.
    When I create order ${order2} and store result in created
    Then I expect that order ${created} has id assigned
