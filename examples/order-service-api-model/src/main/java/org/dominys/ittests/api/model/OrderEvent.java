package org.dominys.ittests.api.model;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * A notification the Order service publishes to Kafka when something happens to
 * an order.
 *
 * <p>This is the <em>wire contract</em> shared by the service (producer) and the
 * Kafka example scenarios (consumer). Keeping it in the api-model module - next
 * to {@link Order} and {@link StatusUpdateCommand} - means both sides serialize
 * and deserialize the exact same shape, exactly like a real published event
 * schema.
 *
 * <p>Fields the scenarios match on:
 * <ul>
 *     <li>{@code type} - {@link OrderEventType#ORDER_CREATED} or
 *         {@link OrderEventType#ORDER_STATUS_UPDATED};</li>
 *     <li>{@code orderId} - lets a scenario filter down to <em>its own</em>
 *         order when many scenarios run in parallel on the same topic;</li>
 *     <li>{@code status} - the order's status at the moment the event fired.</li>
 * </ul>
 */
@Jacksonized
@Builder
public record OrderEvent(String eventId,
                         OrderEventType type,
                         String orderId,
                         OrderStatus status,
                         double price,
                         Instant occurredAt) {
}
