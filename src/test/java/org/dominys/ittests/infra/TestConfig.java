package org.dominys.ittests.infra;

import org.dominys.ittests.sut.OrderService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal Spring configuration for the test context.
 *
 * <p>It component-scans the infra package so the glue-scoped beans
 * ({@link ScenarioContext}, {@link ScenarioContextStore}) are managed by Spring,
 * and registers the sample {@link OrderService} as the "system under test".
 *
 * <p>In a real suite this is where you would instead declare HTTP clients,
 * message producers, test-container connections, etc.
 */
@Configuration
@ComponentScan(basePackageClasses = ScenarioContext.class)
public class TestConfig {

    @Bean
    public OrderService orderService() {
        return new OrderService();
    }
}
