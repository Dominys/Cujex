package io.github.dominys.cujex.kafka;

import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

/**
 * A per-scenario mailbox of Kafka messages - the piece that makes a single shared
 * set of topic consumers safe to use from many scenarios at once, including in
 * parallel.
 *
 * <p>How it fits together: every active scenario gets its own inbox (Spring's
 * {@code cucumber-glue} scope gives one instance per scenario). On construction it
 * {@linkplain KafkaMessageBus#register registers} with the bus, so from then on it
 * receives a copy of <em>every</em> message any topic consumer sees, buffered into
 * a thread-safe queue. A scenario then calls {@link #awaitMessage} with a
 * {@link Predicate} that matches only the messages belonging to it (typically by an
 * id the scenario created) - other scenarios' traffic is simply ignored. When the
 * scenario ends, Cucumber disposes the glue bean and {@link #close} unregisters the
 * inbox, so no leakage between scenarios.
 */
public class ScenarioKafkaInbox implements AutoCloseable {

    private final ConcurrentLinkedQueue<KafkaMessage> received = new ConcurrentLinkedQueue<>();
    private final Duration defaultTimeout;
    private final AutoCloseable registration;

    public ScenarioKafkaInbox(KafkaMessageBus bus, CujexKafkaSettings settings) {
        this.defaultTimeout = settings.messageAwaitTimeout();
        this.registration = bus.register(received::add);
    }

    /** Wait for a message matching {@code criteria}, using the configured default timeout. */
    public KafkaMessage awaitMessage(Predicate<KafkaMessage> criteria) {
        return awaitMessage(criteria, defaultTimeout);
    }

    /** Wait up to {@code timeout} for a message matching {@code criteria}, then return it. */
    public KafkaMessage awaitMessage(Predicate<KafkaMessage> criteria, Duration timeout) {
        return Awaitility.await("Kafka message matching scenario criteria")
                .atMost(timeout)
                .pollInterval(Duration.ofMillis(100))
                .until(() -> firstMatch(criteria), Objects::nonNull);
    }

    /** All messages received so far that match {@code criteria} (useful for assertions on counts). */
    public List<KafkaMessage> matching(Predicate<KafkaMessage> criteria) {
        return received.stream().filter(criteria).toList();
    }

    private KafkaMessage firstMatch(Predicate<KafkaMessage> criteria) {
        return received.stream().filter(criteria).findFirst().orElse(null);
    }

    @Override
    public void close() throws Exception {
        registration.close();
        received.clear();
    }
}
