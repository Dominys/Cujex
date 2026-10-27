package org.dominys.ittests.infra;

import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import org.dominys.ittests.sut.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tells cucumber-spring how to bootstrap the Spring {@link org.springframework.context.ApplicationContext}
 * used by every scenario.
 *
 * <p>{@code @CucumberContextConfiguration} must appear on exactly one glue class.
 * Here it is paired with a plain {@code @ContextConfiguration} (no Spring Boot) to
 * keep the example lightweight while still registering the {@code "cucumber-glue"}
 * scope that {@link ScenarioContext} / {@link ScenarioContextStore} rely on.
 */
@CucumberContextConfiguration
@ContextConfiguration(classes = TestConfig.class)
public class CucumberSpringConfiguration {

    @Autowired
    private OrderService orderService;

    /**
     * The Spring context (and therefore the singleton {@link OrderService}) is
     * shared across scenarios, so its state is reset before each one. Note the
     * glue-scoped {@link ScenarioContextStore} needs no such reset - cucumber-spring
     * gives each scenario a brand-new instance.
     */
    @Before
    public void resetSystemUnderTest() {
        orderService.reset();
    }
}
