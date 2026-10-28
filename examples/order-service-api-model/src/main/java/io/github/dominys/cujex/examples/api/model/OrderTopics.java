package io.github.dominys.cujex.examples.api.model;

/**
 * Kafka topic names shared by the Order service and the example test suite.
 *
 * <p>Centralising the names here (part of the wire contract) keeps the producer,
 * the consumer and the tests from drifting apart over a stray string literal.
 */
public final class OrderTopics {

    /** Outbound domain events the service publishes ({@link OrderEvent}). */
    public static final String ORDER_EVENTS = "order-events";

    /** Inbound commands the service consumes ({@link StatusUpdateCommand}). */
    public static final String ORDER_STATUS_UPDATES = "order-status-updates";

    private OrderTopics() {
    }
}
