package io.github.dominys.cujex.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A Kafka record as the test side sees it: the raw {@code byte[]} key and value exactly
 * as they came off the broker, plus a little envelope metadata.
 *
 * <p><b>Why bytes, not {@code String}?</b> Kafka's payload is always bytes; text is just
 * one encoding of them. Carrying raw bytes keeps Cujex agnostic of the wire format, so
 * the same harness works for JSON, Protobuf, Avro or anything else. A suite's step
 * definitions decode {@link #value()} into whatever domain type they expect - there are
 * two convenient ways to do so:
 * <ul>
 *     <li>for text/JSON, {@link #valueAsString()} gives the UTF-8 string (then feed it to
 *         Jackson, as the example {@code OrderKafkaSteps} does);</li>
 *     <li>for any format, {@link #value(PayloadDecoder)} applies a {@link PayloadDecoder}
 *         - e.g. {@code message.value(OrderEvent::parseFrom)} for Protobuf.</li>
 * </ul>
 *
 * <p>Being a record over {@code byte[]} fields, the generated {@code equals}/
 * {@code hashCode}/{@code toString} would compare array identity, not contents; they are
 * overridden here to use {@link Arrays} so two messages with equal bytes compare equal
 * and print their payloads. Matching still happens through predicates in
 * {@link ScenarioKafkaInbox}, never by equality.
 *
 * @param topic     topic the record came from
 * @param key       raw record key (may be {@code null})
 * @param value     raw record value (may be {@code null})
 * @param timestamp broker/record timestamp
 * @param headers   record headers decoded as UTF-8 strings
 */
public record KafkaMessage(String topic,
                           byte[] key,
                           byte[] value,
                           Instant timestamp,
                           Map<String, String> headers) {

    public KafkaMessage {
        headers = nullTolerantCopy(headers);
    }

    /**
     * An unmodifiable snapshot of {@code headers} that tolerates {@code null} values.
     *
     * <p>Kafka header values are legitimately nullable, and {@link KafkaTopicConsumer}
     * maps a null header value to a null entry. {@link Map#copyOf} would reject those
     * with a {@link NullPointerException}, which - thrown from the poll loop - would
     * silently kill the topic consumer, so a {@link LinkedHashMap} copy is used instead.
     */
    private static Map<String, String> nullTolerantCopy(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /** The key decoded as UTF-8 text, or {@code null} if the key is {@code null}. */
    public String keyAsString() {
        return key == null ? null : new String(key, StandardCharsets.UTF_8);
    }

    /** The value decoded as UTF-8 text, or {@code null} if the value is {@code null}. */
    public String valueAsString() {
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    /**
     * Decode the value with a {@link PayloadDecoder} - the format-agnostic way to read a
     * message, e.g. {@code message.value(OrderEvent::parseFrom)}. Any decoding failure is
     * rethrown unchecked.
     *
     * @param decoder turns the raw value bytes into {@code T}
     * @param <T>     decoded type
     * @return the decoded value
     */
    public <T> T value(PayloadDecoder<T> decoder) {
        try {
            return decoder.decode(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode value from topic '" + topic + "'", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof KafkaMessage(String t, byte[] k, byte[] v, Instant ts, Map<String, String> h)
                && Objects.equals(topic, t)
                && Arrays.equals(key, k)
                && Arrays.equals(value, v)
                && Objects.equals(timestamp, ts)
                && Objects.equals(headers, h);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(topic, timestamp, headers);
        result = 31 * result + Arrays.hashCode(key);
        result = 31 * result + Arrays.hashCode(value);
        return result;
    }

    @Override
    public String toString() {
        return "KafkaMessage[topic=" + topic
                + ", key=" + Arrays.toString(key)
                + ", value=" + Arrays.toString(value)
                + ", timestamp=" + timestamp
                + ", headers=" + headers + ']';
    }
}
