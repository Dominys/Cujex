/**
 * Optional Kafka support for Cujex suites.
 *
 * <p>cujex-core stays entirely Kafka-free; a suite opts in by depending on this
 * module and importing {@link io.github.dominys.cujex.kafka.CujexKafkaConfig}. The
 * moving parts:
 * <ul>
 *     <li>{@link io.github.dominys.cujex.kafka.CujexKafkaSettings} - the suite's
 *         description of broker, consumed topics, required groups and timeouts.</li>
 *     <li>{@link io.github.dominys.cujex.kafka.KafkaConsumerManager} /
 *         {@code KafkaTopicConsumer} - one long-lived consumer per topic for the
 *         whole run.</li>
 *     <li>{@link io.github.dominys.cujex.kafka.KafkaMessageBus} - fans each record
 *         out to every active scenario.</li>
 *     <li>{@link io.github.dominys.cujex.kafka.ScenarioKafkaInbox} - a glue-scoped,
 *         per-scenario mailbox that filters and awaits messages with Awaitility.</li>
 *     <li>{@link io.github.dominys.cujex.kafka.KafkaConsumerGroupWaiter} - blocks at
 *         startup until the suite's required consumer groups are active.</li>
 *     <li>{@link io.github.dominys.cujex.kafka.KafkaProducerSupport} - a simple
 *         producer for driving the system under test.</li>
 *     <li>{@link io.github.dominys.cujex.kafka.KafkaMessage} /
 *         {@link io.github.dominys.cujex.kafka.PayloadDecoder} - a payload-agnostic
 *         transport: records carry raw {@code byte[]}, decoded at the step edge as a
 *         UTF-8 String or via a pluggable decoder (Protobuf, Avro, JSON, ...).</li>
 * </ul>
 */
package io.github.dominys.cujex.kafka;
