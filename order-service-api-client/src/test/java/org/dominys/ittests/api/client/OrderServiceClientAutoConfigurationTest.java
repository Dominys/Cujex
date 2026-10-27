package org.dominys.ittests.api.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link OrderServiceClientAutoConfiguration} through its conditions with an
 * {@link ApplicationContextRunner}. No server is started and no HTTP request is issued -
 * the {@link OrderApi} bean is only a lazily-built proxy, so creating it is harmless.
 */
class OrderServiceClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OrderServiceClientAutoConfiguration.class));

    @Test
    void registersOrderApiWhenBaseUrlIsSet() {
        runner.withPropertyValues("order-service.base-url=http://localhost:8080")
                .run(context -> assertThat(context)
                        .hasSingleBean(OrderApi.class)
                        .hasSingleBean(OrderServiceClientProperties.class));
    }

    @Test
    void bindsBaseUrlProperty() {
        runner.withPropertyValues("order-service.base-url=http://orders.example:9000")
                .run(context -> assertThat(context.getBean(OrderServiceClientProperties.class).baseUrl())
                        .isEqualTo("http://orders.example:9000"));
    }

    @Test
    void staysDormantWhenBaseUrlIsMissing() {
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(OrderApi.class)
                .doesNotHaveBean(OrderServiceClientProperties.class));
    }

    @Test
    void backsOffWhenApplicationDefinesItsOwnOrderApi() {
        var userBean = OrderServiceClient.create("http://user-supplied:1234");
        runner.withPropertyValues("order-service.base-url=http://localhost:8080")
                .withBean("customOrderApi", OrderApi.class, () -> userBean)
                .run(context -> assertThat(context)
                        .hasSingleBean(OrderApi.class)
                        .getBean(OrderApi.class)
                        .isSameAs(userBean));
    }
}
