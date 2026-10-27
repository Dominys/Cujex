# Use case 1: scenario context storage - the create / store / reference / read-back loop.
#
# This is the most important pattern in the whole example. Watch how a value
# produced by one step (the server-assigned order) is stored under a key and then
# referenced by later steps through ${...}.
@ContextStorage
Feature: Storing step results in the scenario context

  Scenario: Create an order, then reference the created result in later steps

    # 1. Build an input entity from a table. The `cucId` column names the row;
    #    the entity is stored under that name, so it becomes referenceable as ${order1}.
    Given I prepare orders:
      | cucId  | item   | quantity | price |
      | order1 | widget | 3        | 9.99  |

    # 2. Call the service with the stored input (${order1} is resolved to the Order
    #    object) and store the *response* under a new key: `created`.
    When I create order ${order1} and store result in created

    # 3. The service assigned an id. We can navigate the stored result with an
    #    accessor call inside a JEXL expression: ${created.id()}.
    Then I expect that order ${created} has id assigned
    And I expect that order ${created} has status NEW

    # 4. Read it back by id and store under `fetched`, then compare the two stored
    #    objects. This proves the round-trip and the store survived across steps.
    When I read order with id ${created.id()} and store result in fetched
    Then I expect that ${fetched} equals ${created}
