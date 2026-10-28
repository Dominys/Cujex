package io.github.dominys.cujex.kafka;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaMessageTest {

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static KafkaMessage message(byte[] key, byte[] value) {
        return new KafkaMessage("orders", key, value, Instant.EPOCH, Map.of());
    }

    @Test
    void decodesKeyAndValueAsUtf8() {
        var message = message(utf8("k1"), utf8("hello"));

        assertThat(message.keyAsString()).isEqualTo("k1");
        assertThat(message.valueAsString()).isEqualTo("hello");
    }

    @Test
    void toleratesNullKeyAndValue() {
        var message = message(null, null);

        assertThat(message.keyAsString()).isNull();
        assertThat(message.valueAsString()).isNull();
    }

    @Test
    void decodesValueWithACustomDecoder() {
        var message = message(null, utf8("42"));
        PayloadDecoder<Integer> toInt = bytes -> Integer.parseInt(new String(bytes, StandardCharsets.UTF_8));

        assertThat(message.value(toInt)).isEqualTo(42);
    }

    @Test
    void wrapsDecoderFailuresWithTheTopic() {
        var message = message(null, utf8("not-a-number"));
        PayloadDecoder<Integer> toInt = bytes -> Integer.parseInt(new String(bytes, StandardCharsets.UTF_8));

        assertThatThrownBy(() -> message.value(toInt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("orders")
                .hasCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    void copiesNullHeadersToAnEmptyMap() {
        var message = new KafkaMessage("orders", null, null, Instant.EPOCH, null);

        assertThat(message.headers()).isEmpty();
    }

    @Test
    void keepsNullHeaderValuesInAnUnmodifiableCopy() {
        var source = new LinkedHashMap<String, String>();
        source.put("trace", null);

        var message = new KafkaMessage("orders", null, null, Instant.EPOCH, source);

        assertThat(message.headers()).containsEntry("trace", null);
        assertThatThrownBy(() -> message.headers().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void decouplesHeadersFromTheSourceMap() {
        var source = new HashMap<String, String>();
        source.put("trace", "abc");

        var message = new KafkaMessage("orders", null, null, Instant.EPOCH, source);
        source.put("trace", "mutated");

        assertThat(message.headers()).containsEntry("trace", "abc");
    }

    @Test
    void comparesByContentNotArrayIdentity() {
        var first = message(utf8("k"), utf8("v"));
        var second = message(utf8("k"), utf8("v"));

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void differsWhenValueBytesDiffer() {
        assertThat(message(utf8("k"), utf8("v1"))).isNotEqualTo(message(utf8("k"), utf8("v2")));
    }

    @Test
    void rendersPayloadsInToString() {
        assertThat(message(utf8("k"), utf8("v")).toString())
                .contains("topic=orders")
                .contains("headers={}");
    }
}
