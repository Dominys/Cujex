package io.github.dominys.cujex.kafka;

import java.time.Duration;
import java.util.List;

/**
 * Everything the Kafka extension needs to know about <em>your</em> broker and
 * topics. A suite supplies one of these as a Spring bean; {@link CujexKafkaConfig}
 * wires the rest of the machinery from it.
 *
 * <p>Kept deliberately small and explicit so a suite stays in control of:
 * <ul>
 *     <li>{@code bootstrapServers} - where the broker is (often a Testcontainers
 *         address resolved at runtime);</li>
 *     <li>{@code consumedTopics} - the topics Cujex should tail for the whole run,
 *         one long-lived consumer each, fanning records out to every scenario;</li>
 *     <li>{@code requiredConsumerGroups} - groups (usually the service's own) that
 *         must be <em>stable with assigned partitions</em> before scenarios start,
 *         so the first test message is never produced into the void;</li>
 *     <li>{@code consumerGroupReadyTimeout} / {@code messageAwaitTimeout} - how long
 *         to wait for group readiness and for an expected message, respectively.</li>
 * </ul>
 *
 * @param bootstrapServers          Kafka bootstrap servers, {@code host:port[,host:port]}
 * @param consumedTopics            topics Cujex tails for the whole run
 * @param requiredConsumerGroups    consumer groups that must be ready before scenarios run
 * @param consumerGroupReadyTimeout how long to wait for those groups to become ready
 * @param messageAwaitTimeout       default per-scenario wait for an expected message
 */
public record CujexKafkaSettings(String bootstrapServers,
                                 List<String> consumedTopics,
                                 List<String> requiredConsumerGroups,
                                 Duration consumerGroupReadyTimeout,
                                 Duration messageAwaitTimeout) {

    /**
     * Convenience constructor with sensible default timeouts (60s for group
     * readiness, 20s for message waits) for the common case.
     */
    public CujexKafkaSettings(String bootstrapServers,
                              List<String> consumedTopics,
                              List<String> requiredConsumerGroups) {
        this(bootstrapServers,
                consumedTopics,
                requiredConsumerGroups,
                Duration.ofSeconds(60),
                Duration.ofSeconds(20));
    }

    public CujexKafkaSettings {
        consumedTopics = List.copyOf(consumedTopics);
        requiredConsumerGroups = List.copyOf(requiredConsumerGroups);
    }
}
