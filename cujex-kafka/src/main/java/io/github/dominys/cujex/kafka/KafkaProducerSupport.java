package io.github.dominys.cujex.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * A minimal, suite-lifetime Kafka producer that scenarios use to drive the system
 * under test - for example to send a command the service consumes.
 *
 * <p>Records are sent as raw {@code byte[]}, mirroring {@link KafkaTopicConsumer} on the
 * read side, so the producer is agnostic of the wire format. Steps pick the overload that
 * fits their payload: {@link #send(String, String, String)} UTF-8-encodes text/JSON,
 * while {@link #send(String, String, byte[])} and {@link #send(String, byte[], byte[])}
 * send binary values such as Protobuf or Avro. {@link #send} uses {@code acks=all} and
 * flushes so the record is on the broker before the step returns, giving tests
 * deterministic ordering without fiddling with futures.
 */
public class KafkaProducerSupport implements AutoCloseable {

    private final KafkaProducer<byte[], byte[]> producer;

    public KafkaProducerSupport(String bootstrapServers) {
        var props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        this.producer = new KafkaProducer<>(props);
    }

    /** Send a raw binary record - the format-agnostic primitive (Protobuf, Avro, ...). */
    public void send(String topic, byte[] key, byte[] value) {
        producer.send(new ProducerRecord<>(topic, key, value));
        producer.flush();
    }

    /** Send a binary value under a text key (UTF-8 encoded); the common Protobuf/Avro case. */
    public void send(String topic, String key, byte[] value) {
        send(topic, utf8(key), value);
    }

    /** Convenience for text/JSON payloads: UTF-8 encodes both key and value. */
    public void send(String topic, String key, String value) {
        send(topic, utf8(key), utf8(value));
    }

    private static byte[] utf8(String s) {
        return s == null ? null : s.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        producer.close();
    }
}
