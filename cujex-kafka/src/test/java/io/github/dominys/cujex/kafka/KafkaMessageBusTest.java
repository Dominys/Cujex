package io.github.dominys.cujex.kafka;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaMessageBusTest {

    private final KafkaMessageBus bus = new KafkaMessageBus();

    private static KafkaMessage message(String topic) {
        return new KafkaMessage(topic, null, null, Instant.EPOCH, Map.of());
    }

    @Test
    void fansMessagesOutToEveryRegisteredSink() {
        var first = new ArrayList<KafkaMessage>();
        var second = new ArrayList<KafkaMessage>();
        bus.register(first::add);
        bus.register(second::add);

        var message = message("orders");
        bus.publish(message);

        assertThat(first).containsExactly(message);
        assertThat(second).containsExactly(message);
    }

    @Test
    void stopsDeliveringAfterTheRegistrationIsClosed() throws Exception {
        var received = new ArrayList<KafkaMessage>();
        var registration = bus.register(received::add);

        registration.close();
        bus.publish(message("orders"));

        assertThat(received).isEmpty();
    }

    @Test
    void verifyHealthyIsANoOpWhenNoFailureReported() {
        bus.verifyHealthy();
    }

    @Test
    void verifyHealthySurfacesTheReportedFailure() {
        var cause = new RuntimeException("boom");
        bus.reportFailure("orders", cause);

        assertThatThrownBy(bus::verifyHealthy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orders")
                .hasCause(cause);
    }
}
