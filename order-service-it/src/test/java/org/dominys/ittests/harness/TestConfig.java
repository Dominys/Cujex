package org.dominys.ittests.harness;

import org.dominys.ittests.context.ScenarioContext;

import org.dominys.ittests.api.client.OrderApi;
import org.dominys.ittests.api.client.OrderServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal Spring configuration for the test context.
 *
 * <p>It component-scans the infra package so the glue-scoped beans
 * ({@link ScenarioContext}, {@link ScenarioContextStore}) are managed by Spring,
 * and registers the {@link OrderApi} REST client as the handle to the
 * "system under test".
 *
 * <p>The client's target URL comes from {@link ServiceUnderTest#baseUrl()}, which
 * either reuses a {@code -Dservice.baseUrl} override or boots the service in a
 * container. This is where a real suite wires up its HTTP clients, message
 * producers, container connections, etc.
 */
@Configuration
@ComponentScan(basePackageClasses = ScenarioContext.class)
public class TestConfig {

    @Bean
    public OrderApi orderApi() {
        return OrderServiceClient.create(ServiceUnderTest.baseUrl());
    }
}
