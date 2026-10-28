# Use case 7: messaging over Kafka with the cujex-kafka extension.
#
# The service publishes an OrderEvent to "order-events" whenever an order is
# created or its status changes, and consumes StatusUpdateCommand messages from
# "order-status-updates". These scenarios drive both directions and show the
# three pieces the extension adds:
#   * one long-lived consumer per topic, shared by every scenario;
#   * a per-scenario inbox that filters to this scenario's own order id, so the
#     suite is safe to run in parallel;
#   * an Awaitility-backed wait for the expected message.
#
# Messages ride as raw byte[], so the harness is format-agnostic; these steps
# decode the value as JSON via a PayloadDecoder, but a Protobuf/Avro suite would
# only swap that decoder.
#
# Every expectation includes the order id this scenario created, which is what
# isolates it from other scenarios producing to the same topic.
@Kafka
Feature: Order messaging over Kafka

  Scenario: Creating an order publishes an ORDER_CREATED event
    Given I prepare orders:
      | cucId  | price | status |
      | order1 | 150.0 | NEW    |
    When I create order ${order1} and store result in created
    Then I await an order event matching and store result in event:
      | type          | orderId         | status |
      | ORDER_CREATED | ${created.id()} | NEW    |
    And I expect that ${event.price()} equals ${150.0}

  Scenario: A status update command drives an ORDER_STATUS_UPDATED event
    Given I prepare orders:
      | cucId  | price | status |
      | order1 | 300.0 | NEW    |
    When I create order ${order1} and store result in created

    # Consume the "created" event first so the later await cannot match it.
    Then I await an order event matching and store result in createdEvent:
      | type          | orderId         |
      | ORDER_CREATED | ${created.id()} |

    When I send a status update command:
      | orderId         | status |
      | ${created.id()} | PAID   |

    Then I await an order event matching and store result in updatedEvent:
      | type                 | orderId         | status |
      | ORDER_STATUS_UPDATED | ${created.id()} | PAID   |

    # The service applied the command, so reading the order back shows PAID.
    When I read order with id ${created.id()} and store result in fetched
    Then I expect that order ${fetched} has status PAID
