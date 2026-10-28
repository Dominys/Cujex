package io.github.dominys.cujex.examples.harness;

import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import io.github.dominys.cujex.examples.api.client.OrderApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tells cucumber-spring how to bootstrap the Spring {@link org.springframework.context.ApplicationContext}
 * used by every scenario.
 *
 * <p>{@code @CucumberContextConfiguration} must appear on exactly one glue class.
 * Here it is paired with a plain {@code @ContextConfiguration} (no Spring Boot) to
 * keep the test context lightweight while still registering the {@code "cucumber-glue"}
 * scope that {@link io.github.dominys.cujex.context.ScenarioContext} /
 * {@link io.github.dominys.cujex.context.ScenarioContextStore} rely on.
 */
@CucumberContextConfiguration
@ContextConfiguration(classes = TestConfig.class)
public class CucumberSpringConfiguration {

    @Autowired
    private OrderApi orderApi;

    /**
     * The service (shared across scenarios because it is one running instance) is
     * reset before each scenario by clearing all orders over the API. The
     * glue-scoped {@link io.github.dominys.cujex.context.ScenarioContextStore} needs no
 * such reset - cucumber-spring gives each scenario a brand-new instance.
     */
    @Before
    public void resetSystemUnderTest() {
        orderApi.deleteAll();
    }
}
