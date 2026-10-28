package io.github.dominys.cujex.examples.api.model;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/**
 * A command the Order service consumes from Kafka to change an order's status.
 *
 * <p>The inbound half of the sample service's <em>wire contract</em>: the Kafka
 * example scenarios publish one of these to {@link OrderTopics#ORDER_STATUS_UPDATES},
 * the service's listener applies it, and the resulting
 * {@link OrderEventType#ORDER_STATUS_UPDATED} event is published back out on
 * {@link OrderTopics#ORDER_EVENTS}.
 *
 * @param orderId id of the order to transition
 * @param status  the status to move the order to
 */
@Jacksonized
@Builder
public record StatusUpdateCommand(String orderId, OrderStatus status) {
}
