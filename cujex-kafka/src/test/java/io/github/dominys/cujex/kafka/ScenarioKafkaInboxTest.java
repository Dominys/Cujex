package io.github.dominys.cujex.kafka;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioKafkaInboxTest {

    private static final Predicate<KafkaMessage> FROM_ORDERS = m -> "orders".equals(m.topic());

    private final KafkaMessageBus bus = new KafkaMessageBus();
    private final CujexKafkaSettings settings = new CujexKafkaSettings(
            "localhost:9092", List.of("orders"), List.of(),
            Duration.ofSeconds(1), Duration.ofSeconds(2));

    private static KafkaMessage message(String topic) {
        return new KafkaMessage(topic, null, null, Instant.EPOCH, Map.of());
    }

    @Test
    void awaitsAMessageMatchingTheCriteria() {
        var inbox = new ScenarioKafkaInbox(bus, settings);
        var expected = message("orders");
        bus.publish(message("other"));
        bus.publish(expected);

        assertThat(inbox.awaitMessage(FROM_ORDERS)).isSameAs(expected);
    }

    @Test
    void matchingReturnsEveryBufferedMatch() {
        var inbox = new ScenarioKafkaInbox(bus, settings);
        bus.publish(message("orders"));
        bus.publish(message("orders"));
        bus.publish(message("other"));

        assertThat(inbox.matching(FROM_ORDERS)).hasSize(2);
    }

    @Test
    void failsFastWhenTheBusIsUnhealthy() {
        var inbox = new ScenarioKafkaInbox(bus, settings);
        bus.reportFailure("orders", new RuntimeException("consumer died"));

        assertThatThrownBy(() -> inbox.awaitMessage(FROM_ORDERS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has stopped");
    }

    @Test
    void stopsReceivingAfterClose() throws Exception {
        var inbox = new ScenarioKafkaInbox(bus, settings);

        inbox.close();
        bus.publish(message("orders"));

        assertThat(inbox.matching(FROM_ORDERS)).isEmpty();
    }
}
