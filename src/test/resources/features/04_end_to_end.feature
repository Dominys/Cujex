# Use case 4: end-to-end showcase tying every mechanism together.
#
# This mirrors the shape of a real service-level scenario: seed variables, build
# related entities that reference each other, call the service, read the result
# back, and assert. It is the closest analogue to the source project's
# create_banner_campaign.feature.
@EndToEnd
Feature: End-to-end order creation showcase

  Scenario: Build related entities, create, and verify the round-trip

    # Shared variables (a past timestamp and a tag list) reused across rows.
    Given I define scenario variables:
      | orderedAt    | ${now('-PT2H')}            |
      | expectedTags | ${[`vip`,`priority`]}      |

    # A customer, referenced by the order below.
    Given I prepare customers:
      | cucId     | name | country |
      | customer1 | Bob  | US      |

    # The order wires together: a plain value (item), typed values (quantity/price),
    # a referenced entity (${customer1}), a variable (${orderedAt}) and a list (${expectedTags}).
    Given I prepare orders:
      | cucId  | item    | quantity | price | status | customer      | createdAt     | tags            |
      | order1 | console | 2        | 299.0 | PAID   | ${customer1}  | ${orderedAt}  | ${expectedTags} |

    When I create order ${order1} and store result in created

    # Server assigned an id but preserved everything we sent...
    Then I expect that order ${created} has id assigned
    And I expect that order ${created} has status PAID
    And I expect that order ${created} matches ${order1} ignoring server fields
    And I expect that ${created.customer().country()} equals ${`US`}
    And I expect that ${created.tags()} contains only ${expectedTags}

    # ...and reading it back yields the exact same object.
    When I read order with id ${created.id()} and store result in fetched
    Then I expect that ${fetched} equals ${created}
