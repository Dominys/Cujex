# cujex-kafka

> Opt-in Kafka extension for [Cujex](../README.md). Core stays messaging-free; a suite
> that needs asynchronous messaging adds this module explicitly.

Kafka support is **not** in `cujex-core` — it lives in this separate, opt-in sibling
module. In the example suite, `07_kafka_messaging.feature` is the worked example.

## What the extension provides

* **One long-lived consumer per topic for the whole run** (`KafkaTopicConsumer`,
  managed by `KafkaConsumerManager`). Consumers are suite-singletons started once at
  context refresh, not per scenario.
* **A fan-out bus** (`KafkaMessageBus`) that delivers every incoming message to all
  active scenario inboxes.
* **A per-scenario inbox** (`ScenarioKafkaInbox`, `@Scope("cucumber-glue")`) that
  buffers messages and exposes an **Awaitility**-backed `awaitMessage(predicate)`.
  Each scenario filters to *its own* criteria (typically the order id it created), so
  scenarios sharing a topic stay isolated — the suite is **parallel-safe**.
* **A consumer-group-readiness wait** (`KafkaConsumerGroupWaiter`): because group
  formation takes time, the manager uses the Kafka Admin API + Awaitility to block at
  startup until every required group is `STABLE` with assigned partitions, before any
  scenario runs.
* **A simple producer** (`KafkaProducerSupport`, `acks=all`, flush) for driving input
  topics from steps. It sends raw `byte[]`, with `String` overloads that UTF-8-encode
  text/JSON so the common cases stay one-liners.
* **A payload-agnostic transport.** `KafkaMessage` carries the record's raw `byte[]`
  key/value — what Kafka actually transmits — so the harness is not tied to String/JSON.
  Steps decode at the edge: `valueAsString()` for text/JSON, or `value(PayloadDecoder)`
  for any binary format (e.g. `message.value(OrderEvent::parseFrom)` for **Protobuf**,
  or an Avro/JSON reader). The fan-out bus and inbox stay format-neutral.

## Wiring it onto a suite

See `TestConfig` + the `order-service-it` harness.

1. **Depend on it** (test scope), alongside a Kafka Testcontainer:
   ```xml
   <dependency>
       <groupId>io.github.dominys</groupId>
       <artifactId>cujex-kafka</artifactId>
       <scope>test</scope>
   </dependency>
   <dependency>
       <groupId>org.testcontainers</groupId>
       <artifactId>testcontainers-kafka</artifactId>
       <scope>test</scope>
   </dependency>
   ```
2. **Import the lite config** (it has no stereotype, so it is activated only by an
   explicit import — it never leaks into the core component-scan):
   ```java
   @Import({ CujexCoreConfig.class, CujexKafkaConfig.class })
   ```
3. **Provide a `CujexKafkaSettings` bean** naming the bootstrap servers, the topics to
   consume, and the consumer groups to await:
   ```java
   @Bean
   @DependsOn("orderApi")            // ensure the service container is up first
   CujexKafkaSettings cujexKafkaSettings() {
       return new CujexKafkaSettings(
           KafkaUnderTest.bootstrapServers(),
           List.of(OrderTopics.ORDER_EVENTS),
           List.of("order-service"));
   }
   ```

## Container ordering

The broker must be up before the service (the service reads its bootstrap address from
the environment) and before the Cujex consumers start. This is enforced entirely in the
*example*, not the library: `ServiceUnderTest` joins the shared `KafkaUnderTest` network
and passes the in-network bootstrap address as an env var (touching `KafkaUnderTest`
starts the broker), and the `CujexKafkaSettings` bean is `@DependsOn("orderApi")` so the
group-readiness wait only runs once the service is live.

## Steps

`OrderKafkaSteps` (extends `BaseSteps`) drives the producer and the await:

```gherkin
When I send a status update command:
  | orderId         | status |
  | ${created.id()} | PAID   |

Then I await an order event matching and store result in event:
  | type          | orderId         | status |
  | ORDER_CREATED | ${created.id()} | NEW    |
```

The await step builds a predicate from the table (every cell resolved through JEXL),
reads each buffered message's value as a UTF-8 `String` and matches its JSON
field-by-field; on the first match it decodes the event via a `PayloadDecoder`
(`message.value(bytes -> OBJECT_MAPPER.readValue(bytes, OrderEvent.class))`) and stores
it under the result key so later steps can assert on it with the generic validation
steps. A Protobuf suite would swap only that decoder for `OrderEvent::parseFrom` — the
rest of the extension is unchanged.

## Module layout

```
cujex-kafka/src/main/java/io/github/dominys/cujex/kafka/
├── CujexKafkaConfig.java          lite @Configuration, activated only by @Import
├── CujexKafkaSettings.java        bootstrap servers + topics + groups to await
├── KafkaConsumerGroupWaiter.java  Admin API + Awaitility group-readiness wait
├── KafkaConsumerManager.java      starts/stops one consumer per topic for the run
├── KafkaTopicConsumer.java        long-lived per-topic consumer loop
├── KafkaMessageBus.java           fan-out of messages to active scenario inboxes
├── ScenarioKafkaInbox.java        @Scope("cucumber-glue") buffer + awaitMessage(...)
├── KafkaMessage.java              payload-agnostic record (raw byte[] key/value)
├── PayloadDecoder.java            functional decoder: byte[] -> T
└── KafkaProducerSupport.java      acks=all producer, byte[] + String overloads
```
