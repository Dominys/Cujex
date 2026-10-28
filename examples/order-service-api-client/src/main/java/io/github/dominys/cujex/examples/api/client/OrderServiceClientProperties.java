package io.github.dominys.cujex.examples.api.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Order service client.
 *
 * <p>Bound from the {@code order-service.*} namespace, e.g. in
 * {@code application.yml}:
 * <pre>
 * order-service:
 *   base-url: http://localhost:8080
 * </pre>
 *
 * @param baseUrl root URL of a running Order service, e.g. {@code http://localhost:8080}
 */
@ConfigurationProperties(prefix = "order-service")
public record OrderServiceClientProperties(String baseUrl) {
}
