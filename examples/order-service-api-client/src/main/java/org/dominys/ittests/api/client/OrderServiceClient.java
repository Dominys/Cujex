package org.dominys.ittests.api.client;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Factory that turns a base URL into a ready-to-use {@link OrderApi} proxy.
 *
 * <p>Wiring: a {@link RestClient} pointed at {@code baseUrl} is wrapped in a
 * {@link RestClientAdapter} and handed to an {@link HttpServiceProxyFactory},
 * which generates the {@link OrderApi} implementation. Tests call
 * {@code OrderServiceClient.create("http://localhost:8080")} and get back a
 * client that issues real HTTP requests.
 */
public final class OrderServiceClient {

    private OrderServiceClient() {
    }

    /**
     * Build an {@link OrderApi} client targeting the service at {@code baseUrl}.
     *
     * @param baseUrl root URL of a running Order service, e.g. {@code http://localhost:8080}
     */
    public static OrderApi create(String baseUrl) {
        var restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        var factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(OrderApi.class);
    }
}
