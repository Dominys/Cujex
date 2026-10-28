package io.github.dominys.cujex.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

/**
 * The opt-in wiring for the Kafka extension. A suite activates Kafka support by
 * importing this class from its own test configuration
 * ({@code @Import(CujexKafkaConfig.class)}); nothing here is component-scanned.
 *
 * <p>This is intentionally <b>not</b> annotated with {@code @Configuration}. cujex-core
 * component-scans its own package, and a stereotype here would risk auto-activating
 * Kafka beans for suites that never asked for them. Declaring plain {@code @Bean}
 * methods on a non-stereotyped class makes Spring treat it as a "lite"
 * configuration: the beans are still registered when the class is imported, but
 * there is no CGLIB proxy, so beans are wired through method parameters (never by
 * calling another {@code @Bean} method directly).
 *
 * <p>Lifecycle is expressed with {@code initMethod}/{@code destroyMethod} instead of
 * {@code jakarta.annotation} callbacks, keeping this module's dependency surface to
 * just the Kafka client, Awaitility and spring-context.
 *
 * <p>The suite must still provide one bean this config consumes: a
 * {@link CujexKafkaSettings} describing the broker, topics and required groups.
 */
public class CujexKafkaConfig {

    @Bean
    public KafkaMessageBus cujexKafkaMessageBus() {
        return new KafkaMessageBus();
    }

    @Bean(destroyMethod = "close")
    public KafkaConsumerGroupWaiter cujexKafkaConsumerGroupWaiter(CujexKafkaSettings settings) {
        return new KafkaConsumerGroupWaiter(settings.bootstrapServers());
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    public KafkaConsumerManager cujexKafkaConsumerManager(CujexKafkaSettings settings,
                                                          KafkaMessageBus bus,
                                                          KafkaConsumerGroupWaiter groupWaiter) {
        return new KafkaConsumerManager(settings, bus, groupWaiter);
    }

    @Bean(destroyMethod = "close")
    public KafkaProducerSupport cujexKafkaProducerSupport(CujexKafkaSettings settings) {
        return new KafkaProducerSupport(settings.bootstrapServers());
    }

    @Bean(destroyMethod = "close")
    @Scope("cucumber-glue")
    public ScenarioKafkaInbox scenarioKafkaInbox(KafkaMessageBus bus, CujexKafkaSettings settings) {
        return new ScenarioKafkaInbox(bus, settings);
    }
}
