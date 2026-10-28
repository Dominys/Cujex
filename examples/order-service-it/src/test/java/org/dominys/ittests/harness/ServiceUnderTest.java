package org.dominys.ittests.harness;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Resolves the base URL of the Order service the tests should hit.
 *
 * <p>Two modes, so the same suite works locally and against real environments:
 * <ul>
 *     <li><b>Override</b> - if {@code -Dservice.baseUrl=...} is set, that URL is
 *         used as-is and nothing is started. Point it at any running instance.</li>
 *     <li><b>Testcontainers (default)</b> - build the service image from
 *         {@code ../order-service/Dockerfile} and start it once for the whole
 *         suite, waiting on the actuator health endpoint before returning.</li>
 * </ul>
 *
 * <p>The container is a lazily-created static singleton: Cucumber (not a JUnit
 * extension) drives the lifecycle here, so starting it on first access and
 * letting the JVM tear it down keeps one service instance alive across every
 * scenario.
 */
@SuppressWarnings("java:S2187") // Not a JUnit test despite the "...Test" name; it is the SUT locator.
public final class ServiceUnderTest {

    private static final String BASE_URL_PROPERTY = "service.baseUrl";
    private static final int SERVICE_PORT = 8080;

    private static volatile String resolvedBaseUrl;

    private ServiceUnderTest() {
    }

    /** Base URL of a ready-to-use Order service, starting a container if needed. */
    public static String baseUrl() {
        var override = System.getProperty(BASE_URL_PROPERTY);
        if (override != null && !override.isBlank()) {
            return override;
        }
        return containerBaseUrl();
    }

    private static String containerBaseUrl() {
        if (resolvedBaseUrl == null) {
            synchronized (ServiceUnderTest.class) {
                if (resolvedBaseUrl == null) {
                    var container = Holder.CONTAINER;
                    container.start();
                    resolvedBaseUrl = "http://" + container.getHost()
                            + ":" + container.getMappedPort(SERVICE_PORT);
                }
            }
        }
        return resolvedBaseUrl;
    }

    /** Lazy holder so the image is only built when the container path is actually taken. */
    private static final class Holder {
        private static final GenericContainer<?> CONTAINER = buildContainer();

        private static GenericContainer<?> buildContainer() {
            var serviceModule = Path.of("..", "order-service");
            var image = new ImageFromDockerfile()
                    .withFileFromPath(".", serviceModule);
            // Join Kafka's network and point the service at the in-network broker.
            // Reaching KafkaUnderTest here guarantees the broker starts first.
            return new GenericContainer<>(image)
                    .withExposedPorts(SERVICE_PORT)
                    .withNetwork(KafkaUnderTest.network())
                    .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS",
                            KafkaUnderTest.internalBootstrapServers())
                    .waitingFor(Wait.forHttp("/actuator/health")
                            .forPort(SERVICE_PORT)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(2)));
        }
    }
}
