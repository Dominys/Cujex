# Use case 2: JEXL preprocessing of cells.
#
# Every data-table cell and inline {param} is passed through ScenarioContext.resolveField
# BEFORE Jackson maps it. A cell shaped like ${ ... } is evaluated as a JEXL expression;
# anything else is left as a plain string. This feature shows the main expression kinds.
@Jexl
Feature: JEXL preprocessing of feature-file values

  Scenario: Seed variables with functions and literals, then use them

    # "I define scenario variables" stores each resolved value in the context.
    #  - ${now('-PT1H')}  -> custom JEXL function (CucJexlFunctions.now) returning an Instant
    #  - ${[`vip`,`priority`]} -> a JEXL literal list of strings (backticks are JEXL string literals)
    Given I define scenario variables:
      | createdAt    | ${now('-PT1H')}        |
      | expectedTags | ${[`vip`,`priority`]}  |

    # Those variables are now referenceable in later cells by name.
    #  - ${createdAt}   -> injects the Instant built above
    #  - ${expectedTags}-> injects the list built above
    #  - price is a plain string, mapped to double by Jackson
    Given I prepare orders:
      | cucId  | price | tags            | createdAt    |
      | order1 | 19.50 | ${expectedTags} | ${createdAt} |

    When I create order ${order1} and store result in created

    # Navigate into the created object and compare its list field against the
    # list literal we stored earlier.
    Then I expect that ${created.tags()} contains only ${expectedTags}

  Scenario: Inline expressions can navigate nested entities

    Given I prepare customers:
      | cucId     | name  | country |
      | customer1 | Alice | PL      |

    # ${customer1} resolves the stored Customer and injects it into the order's
    # `customer` field (Jackson maps the nested object).
    Given I prepare orders:
      | cucId  | price | customer     |
      | order1 | 5.00  | ${customer1} |

    When I create order ${order1} and store result in created

    # Deep navigation through accessors: order -> customer -> country.
    Then I expect that order ${created} has status NEW
    And I expect that ${created.customer().country()} equals ${`PL`}
