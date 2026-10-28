package org.dominys.ittests.service;

import org.apache.kafka.clients.admin.NewTopic;
import org.dominys.ittests.api.model.OrderTopics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the Kafka topics the Order service uses.
 *
 * <p>Spring Kafka creates any {@link NewTopic} bean on the broker at startup (via
 * the auto-configured {@code KafkaAdmin}). Declaring them here means the service
 * owns its topics explicitly rather than relying on broker-side auto-creation,
 * which mirrors how a real service would provision its contract.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(OrderTopics.ORDER_EVENTS).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic orderStatusUpdatesTopic() {
        return TopicBuilder.name(OrderTopics.ORDER_STATUS_UPDATES).partitions(1).replicas(1).build();
    }
}
