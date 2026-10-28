package io.github.dominys.cujex.kafka;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CujexKafkaSettingsTest {

    @Test
    void conveninenceConstructorAppliesDefaultTimeouts() {
        var settings = new CujexKafkaSettings("localhost:9092", List.of("orders"), List.of("order-service"));

        assertThat(settings.consumerGroupReadyTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(settings.messageAwaitTimeout()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void defensivelyCopiesTopicAndGroupLists() {
        var topics = new ArrayList<>(List.of("orders"));
        var groups = new ArrayList<>(List.of("order-service"));

        var settings = new CujexKafkaSettings("localhost:9092", topics, groups);
        topics.add("mutated");
        groups.add("mutated");

        assertThat(settings.consumedTopics()).containsExactly("orders");
        assertThat(settings.requiredConsumerGroups()).containsExactly("order-service");
    }

    @Test
    void exposesImmutableLists() {
        var settings = new CujexKafkaSettings("localhost:9092", List.of("orders"), List.of("order-service"));

        assertThatThrownBy(() -> settings.consumedTopics().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
