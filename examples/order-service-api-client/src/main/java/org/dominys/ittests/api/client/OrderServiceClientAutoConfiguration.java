package org.dominys.ittests.api.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot auto-configuration that exposes a ready-to-use {@link OrderApi} bean.
 *
 * <p>Following the Spring Boot recommendations for a library that ships
 * auto-configuration:
 * <ul>
 *     <li>the class is annotated with {@link AutoConfiguration @AutoConfiguration}
 *         and registered in
 *         {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 *         rather than being component-scanned;</li>
 *     <li>it only kicks in when {@link RestClient} is on the classpath and an
 *         {@code order-service.base-url} property is set
 *         ({@link ConditionalOnProperty @ConditionalOnProperty});</li>
 *     <li>it backs off if the application already defines its own {@link OrderApi}
 *         bean ({@link ConditionalOnMissingBean @ConditionalOnMissingBean}), so the
 *         defaults never fight user-supplied configuration.</li>
 * </ul>
 *
 * <p>A consuming Spring Boot application therefore only needs this module on the
 * classpath plus {@code order-service.base-url} - no client wiring code at all.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "order-service", name = "base-url")
@EnableConfigurationProperties(OrderServiceClientProperties.class)
public class OrderServiceClientAutoConfiguration {

    /**
     * Builds the {@link OrderApi} proxy from {@code order-service.base-url} unless the
     * application has already defined its own.
     */
    @Bean
    @ConditionalOnMissingBean
    public OrderApi orderApi(OrderServiceClientProperties properties) {
        return OrderServiceClient.create(properties.baseUrl());
    }
}
