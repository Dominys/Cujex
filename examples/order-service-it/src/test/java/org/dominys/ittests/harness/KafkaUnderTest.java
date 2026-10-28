package org.dominys.ittests.harness;

import org.testcontainers.containers.Network;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts (once per suite) the Kafka broker the Kafka scenarios run against.
 *
 * <p>Like {@link ServiceUnderTest} this is a lazily-created static singleton:
 * Cucumber drives the lifecycle, so the broker comes up on first access and the
 * JVM tears it down at the end, keeping a single broker alive across every
 * scenario.
 *
 * <p>Two bootstrap addresses are exposed because two different clients connect:
 * <ul>
 *     <li>{@link #bootstrapServers()} - the host-mapped address, used by the
 *         test JVM (Cujex consumers/producers, the admin group waiter).</li>
 *     <li>{@link #internalBootstrapServers()} - the in-network address
 *         ({@code kafka:19092}), handed to the service container so it can reach
 *         the same broker over the shared Docker {@link Network}.</li>
 * </ul>
 *
 * <p>The shared {@link #network()} is also joined by the service container,
 * which guarantees the broker starts before the service (the service's
 * bootstrap env points at it).
 */
@SuppressWarnings("java:S2187") // test infrastructure (named *UnderTest), not a test case
public final class KafkaUnderTest {

    private static final DockerImageName IMAGE = DockerImageName.parse("apache/kafka:4.0.0");
    private static final String NETWORK_ALIAS = "kafka";
    private static final String INTERNAL_BOOTSTRAP = NETWORK_ALIAS + ":19092";

    private KafkaUnderTest() {
    }

    /** Host-reachable {@code host:port} bootstrap address for the test JVM. */
    public static String bootstrapServers() {
        var container = Holder.CONTAINER;
        container.start();
        var servers = container.getBootstrapServers();
        var schemeAt = servers.indexOf("://");
        return schemeAt < 0 ? servers : servers.substring(schemeAt + 3);
    }

    /** In-Docker-network bootstrap address for the service container. */
    public static String internalBootstrapServers() {
        Holder.CONTAINER.start();
        return INTERNAL_BOOTSTRAP;
    }

    /** Shared network the service container joins so it can reach the broker. */
    public static Network network() {
        return Holder.NETWORK;
    }

    /** Lazy holder so the broker is only started when a Kafka scenario needs it. */
    private static final class Holder {
        private static final Network NETWORK = Network.newNetwork();
        private static final KafkaContainer CONTAINER = new KafkaContainer(IMAGE)
                .withNetwork(NETWORK)
                .withNetworkAliases(NETWORK_ALIAS)
                .withListener(INTERNAL_BOOTSTRAP);
    }
}
