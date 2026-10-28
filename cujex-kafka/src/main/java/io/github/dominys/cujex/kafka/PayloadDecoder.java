package io.github.dominys.cujex.kafka;

import java.nio.charset.StandardCharsets;

/**
 * Turns a raw Kafka payload ({@code byte[]}) into a domain object of type {@code T}.
 *
 * <p>Cujex carries every record as raw bytes (see {@link KafkaMessage}) so the harness
 * stays agnostic of the wire format - JSON text, Protobuf, Avro or anything else. A
 * suite supplies a decoder to turn those bytes into whatever it wants to assert on:
 * <pre>{@code
 * // JSON (Jackson), decoding straight from bytes:
 * PayloadDecoder<OrderEvent> JSON = bytes -> MAPPER.readValue(bytes, OrderEvent.class);
 *
 * // Protobuf - parseFrom is a byte[] -> Message method reference:
 * PayloadDecoder<OrderEvent> PROTO = OrderEvent::parseFrom;
 *
 * OrderEvent event = message.value(PROTO);
 * }</pre>
 *
 * <p>The single abstract method declares {@code throws Exception} on purpose: decoders
 * such as Protobuf's {@code parseFrom} or Jackson's {@code readValue} throw checked
 * exceptions, and allowing them here is what lets a plain method reference be used as a
 * decoder. {@link KafkaMessage#value(PayloadDecoder)} wraps any failure in an unchecked
 * exception, so callers never handle checked exceptions themselves.
 *
 * @param <T> the decoded type
 */
@FunctionalInterface
public interface PayloadDecoder<T> {

    /**
     * Decode a payload. Receives the record's raw value bytes (possibly {@code null}
     * for a tombstone) and returns the domain object.
     *
     * <p>Declares {@code throws Exception} deliberately: narrowing it to a specific or
     * custom type would stop common decoders - Protobuf's {@code parseFrom}
     * ({@code InvalidProtocolBufferException}) or Jackson's {@code readValue}
     * ({@code IOException}) - from being usable as plain method references here, which is
     * the whole point of this interface. {@link KafkaMessage#value(PayloadDecoder)}
     * catches and wraps the failure so callers never see a checked exception.
     *
     * @param payload raw record value, may be {@code null}
     * @return the decoded value
     * @throws Exception if the bytes cannot be decoded
     */
    @SuppressWarnings("java:S112") // generic throws is required for method-reference decoders (see above)
    T decode(byte[] payload) throws Exception;

    /**
     * A ready-made decoder for UTF-8 text (e.g. JSON). A {@code null} payload decodes
     * to {@code null}.
     */
    static PayloadDecoder<String> utf8() {
        return payload -> payload == null ? null : new String(payload, StandardCharsets.UTF_8);
    }
}
