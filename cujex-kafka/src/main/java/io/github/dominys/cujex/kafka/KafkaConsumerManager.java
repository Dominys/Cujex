package io.github.dominys.cujex.kafka;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the suite-lifetime topic consumers: one {@link KafkaTopicConsumer} per
 * configured topic, started once before any scenario runs and closed when the
 * Spring context shuts down.
 *
 * <p>Lifecycle (driven by {@link CujexKafkaConfig} via {@code initMethod}/{@code
 * destroyMethod}):
 * <ol>
 *     <li>{@link #start()} spins up every topic consumer, waits for each to be
 *         assigned its partitions, then blocks on
 *         {@link KafkaConsumerGroupWaiter} until the suite's required consumer
 *         groups (usually the service under test's) are stable. Only then is it
 *         safe for scenarios to produce traffic and expect responses.</li>
 *     <li>{@link #close()} stops every consumer.</li>
 * </ol>
 */
@RequiredArgsConstructor
public class KafkaConsumerManager implements AutoCloseable {

    private final CujexKafkaSettings settings;
    private final KafkaMessageBus bus;
    private final KafkaConsumerGroupWaiter groupWaiter;
    private final List<KafkaTopicConsumer> consumers = new ArrayList<>();

    /** Start all topic consumers and wait until everything is ready to test. */
    public void start() {
        for (var topic : settings.consumedTopics()) {
            var consumer = new KafkaTopicConsumer(topic, settings.bootstrapServers(), bus);
            consumer.start();
            consumers.add(consumer);
        }
        for (var consumer : consumers) {
            consumer.awaitAssignment(settings.consumerGroupReadyTimeout());
        }
        groupWaiter.awaitGroupsReady(settings.requiredConsumerGroups(), settings.consumerGroupReadyTimeout());
    }

    @Override
    public void close() {
        consumers.forEach(KafkaTopicConsumer::stop);
        consumers.clear();
    }
}
