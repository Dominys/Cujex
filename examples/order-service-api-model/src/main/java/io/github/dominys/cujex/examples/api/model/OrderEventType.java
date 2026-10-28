package io.github.dominys.cujex.examples.api.model;

/**
 * Kinds of domain event the Order service publishes to Kafka.
 *
 * <p>Part of the sample System Under Test's <em>wire contract</em>: the service
 * emits these over {@link OrderTopics#ORDER_EVENTS} and the Kafka example
 * scenarios assert on them.
 */
public enum OrderEventType {
    /** Emitted right after an order is created. */
    ORDER_CREATED,
    /** Emitted whenever an order's status changes. */
    ORDER_STATUS_UPDATED
}
