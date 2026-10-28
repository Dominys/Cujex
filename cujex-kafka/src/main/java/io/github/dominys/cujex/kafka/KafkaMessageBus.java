package io.github.dominys.cujex.kafka;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A tiny thread-safe publish/subscribe hub sitting between the suite-lifetime
 * topic consumers and the per-scenario inboxes.
 *
 * <p>Why it exists: there is exactly <em>one</em> {@link KafkaTopicConsumer} per
 * topic for the whole run (consumer groups are expensive to create), but many
 * scenarios may be interested in messages at once - possibly in parallel. Each
 * active scenario registers an inbox here; every polled record is fanned out to
 * all of them, and each scenario then filters down to the messages that belong
 * to it (typically by an id it created). Registration returns an
 * {@link AutoCloseable} so a scenario cleanly unsubscribes when it ends.
 */
public class KafkaMessageBus {

    private final Set<Consumer<KafkaMessage>> sinks = ConcurrentHashMap.newKeySet();

    private volatile ConsumerFailure failure;

    /**
     * Subscribe a sink (an inbox) to every message. The returned handle removes
     * the sink when closed; call it from the scenario's teardown.
     */
    public AutoCloseable register(Consumer<KafkaMessage> sink) {
        sinks.add(sink);
        return () -> sinks.remove(sink);
    }

    /** Fan a message out to every currently-registered sink. */
    public void publish(KafkaMessage message) {
        sinks.forEach(sink -> sink.accept(message));
    }

    /**
     * Record that a topic consumer's poll loop has died. There is one consumer per
     * topic for the whole run, so a dead consumer means no further messages will ever
     * arrive; capturing the cause here lets waiters fail fast with the real reason
     * instead of silently timing out.
     */
    public void reportFailure(String topic, Throwable cause) {
        this.failure = new ConsumerFailure(topic, cause);
    }

    /**
     * Throw if any topic consumer has died, surfacing the original cause. Called by
     * inboxes while awaiting a message so scenarios fail fast rather than time out.
     */
    public void verifyHealthy() {
        var current = failure;
        if (current != null) {
            throw new IllegalStateException(
                    "Cujex Kafka consumer for topic '" + current.topic()
                            + "' has stopped; no further messages will arrive", current.cause());
        }
    }

    private record ConsumerFailure(String topic, Throwable cause) {
    }
}
