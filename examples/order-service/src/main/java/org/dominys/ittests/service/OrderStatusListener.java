package org.dominys.ittests.service;

import lombok.RequiredArgsConstructor;
import org.dominys.ittests.api.model.OrderTopics;
import org.dominys.ittests.api.model.StatusUpdateCommand;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link StatusUpdateCommand}s from Kafka and applies them to the order.
 *
 * <p>This is the inbound half of the sample service's messaging contract. Note it
 * delegates straight to {@link OrderService#updateStatus}, the same method the REST
 * controller uses - so a status change made over Kafka emits the very same
 * {@code ORDER_STATUS_UPDATED} event as one made over HTTP. One source of truth for
 * the state transition, two ways in.
 */
@Component
@RequiredArgsConstructor
public class OrderStatusListener {

    private final OrderService orderService;

    @KafkaListener(topics = OrderTopics.ORDER_STATUS_UPDATES, groupId = "order-service")
    public void onStatusUpdate(StatusUpdateCommand command) {
        orderService.updateStatus(command.orderId(), command.status());
    }
}
