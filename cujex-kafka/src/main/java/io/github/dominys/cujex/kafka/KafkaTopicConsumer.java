package io.github.dominys.cujex.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * One long-lived Kafka consumer tailing a single topic for the whole test run and
 * forwarding every record to the {@link KafkaMessageBus}.
 *
 * <p>Design points worth learning:
 * <ul>
 *     <li><b>One per topic, unique group per run.</b> Each run gets a throwaway,
 *         randomly-named group so the test consumer never competes with (or
 *         commits offsets against) the service's own group, and
 *         {@code auto.offset.reset=earliest} means it still sees messages produced
 *         just before it finished joining.</li>
 *     <li><b>Not thread-safe.</b> A {@link KafkaConsumer} may only be touched by one
 *         thread, so all polling happens on a single daemon thread; {@link #stop()}
 *         uses {@link KafkaConsumer#wakeup()} (the one call that <em>is</em> safe
 *         from another thread) to break the poll loop.</li>
 *     <li><b>Assignment latch.</b> {@link #awaitAssignment(Duration)} blocks until a
 *         rebalance has actually handed this consumer its partitions, so callers
 *         know the consumer is live before they start producing test traffic.</li>
 * </ul>
 */
final class KafkaTopicConsumer {

    private static final System.Logger LOG = System.getLogger(KafkaTopicConsumer.class.getName());
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(250);

    private final String topic;
    private final KafkaMessageBus bus;
    private final KafkaConsumer<byte[], byte[]> consumer;
    private final CountDownLatch assigned = new CountDownLatch(1);
    private final Thread pollThread;
    private volatile boolean running = true;

    KafkaTopicConsumer(String topic, String bootstrapServers, KafkaMessageBus bus) {
        this.topic = topic;
        this.bus = bus;
        this.consumer = new KafkaConsumer<>(consumerProps(bootstrapServers));
        this.pollThread = new Thread(this::runPollLoop, "cujex-kafka-" + topic);
        this.pollThread.setDaemon(true);
    }

    private static Properties consumerProps(String bootstrapServers) {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "cujex-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }

    /** Start the daemon poll loop. Returns immediately; use {@link #awaitAssignment}. */
    void start() {
        consumer.subscribe(List.of(topic), new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                // no-op: offsets are never committed for this throwaway group
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                if (!partitions.isEmpty()) {
                    assigned.countDown();
                }
            }
        });
        pollThread.start();
    }

    /** Block until this consumer has been assigned its partition(s), or time out. */
    void awaitAssignment(Duration timeout) {
        try {
            if (!assigned.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "Cujex Kafka consumer for topic '" + topic + "' was not assigned within " + timeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for assignment of topic '" + topic + "'", e);
        }
    }

    private void runPollLoop() {
        try {
            while (running) {
                for (var r : consumer.poll(POLL_TIMEOUT)) {
                    bus.publish(toMessage(r));
                }
            }
        } catch (WakeupException e) {
            // expected on stop()
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR, "Cujex Kafka poll loop for topic '" + topic + "' failed", e);
        } finally {
            consumer.close();
        }
    }

    private KafkaMessage toMessage(ConsumerRecord<byte[], byte[]> r) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header header : r.headers()) {
            headers.put(header.key(),
                    header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8));
        }
        return new KafkaMessage(r.topic(),
                r.key(),
                r.value(),
                Instant.ofEpochMilli(r.timestamp()),
                headers);
    }

    /** Stop polling and release the consumer. Safe to call from another thread. */
    void stop() {
        running = false;
        consumer.wakeup();
        try {
            pollThread.join(TimeUnit.SECONDS.toMillis(10));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
