package org.dominys.ittests.steps;

import org.dominys.ittests.context.StepVariables;

import io.cucumber.java.en.Given;

/**
 * Generic, domain-agnostic steps for seeding the scenario context.
 */
public class InfraSteps extends BaseSteps {

    /**
     * Stores an arbitrary set of named values into the scenario store. Each value
     * is already JEXL-resolved by the {@code stepVariables} data-table type, so you
     * can seed timestamps, arrays, UUIDs, etc.
     *
     * <pre>
     * Given I define scenario variables:
     *   | createdAt | ${now('-PT1H')}        |
     *   | tags      | ${[`vip`,`priority`]}  |
     * </pre>
     *
     * After this step, {@code ${createdAt}} and {@code ${tags}} are referenceable
     * from any later cell.
     */
    @Given("I define scenario variables:")
    public void defineScenarioVariables(StepVariables stepVariables) {
        stepVariables.variables().forEach(v -> scenarioStore().put(v.name(), v.value()));
    }
}
