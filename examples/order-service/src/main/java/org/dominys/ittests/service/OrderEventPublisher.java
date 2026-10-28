package org.dominys.ittests.service;

import lombok.RequiredArgsConstructor;
import org.dominys.ittests.api.model.Order;
import org.dominys.ittests.api.model.OrderEvent;
import org.dominys.ittests.api.model.OrderEventType;
import org.dominys.ittests.api.model.OrderTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@link OrderEvent}s to Kafka when orders change.
 *
 * <p>Sends are fire-and-forget: the returned future is not awaited, so producing a
 * notification never blocks (or fails) the REST call that triggered it. That keeps
 * the existing synchronous HTTP scenarios fast and independent of the broker while
 * the Kafka scenarios still observe the emitted events.
 */
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public void publishOrderCreated(Order order) {
        publish(order, OrderEventType.ORDER_CREATED);
    }

    public void publishOrderStatusUpdated(Order order) {
        publish(order, OrderEventType.ORDER_STATUS_UPDATED);
    }

    private void publish(Order order, OrderEventType type) {
        var event = OrderEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type(type)
                .orderId(order.id())
                .status(order.status())
                .price(order.price())
                .occurredAt(Instant.now())
                .build();
        kafkaTemplate.send(OrderTopics.ORDER_EVENTS, order.id(), event);
    }
}
