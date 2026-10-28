package io.github.dominys.cujex.steps;

import io.github.dominys.cujex.context.ScenarioContext;
import io.github.dominys.cujex.context.ScenarioContextStore;
import io.github.dominys.cujex.context.StepVariables;

import io.cucumber.java.en.Given;

/**
 * Generic, domain-agnostic steps for seeding the scenario context.
 */
public class InfraSteps extends BaseSteps {

    public InfraSteps(ScenarioContext scenarioContext, ScenarioContextStore scenarioStore) {
        super(scenarioContext, scenarioStore);
    }

    /**
     * Stores an arbitrary set of named values into the scenario store. Each row's
     * expression is JEXL-resolved and stored <em>immediately</em>, before the next
     * row is resolved (write-through), so you can seed timestamps, arrays, UUIDs,
     * etc. - and a later row may reference an earlier one defined in the same table.
     *
     * <pre>
     * Given I define scenario variables:
     *   | createdAt | ${now('-PT1H')}        |
     *   | tags      | ${[`vip`,`priority`]}  |
     *   | echoed    | ${createdAt}           |
     * </pre>
     *
     * After this step, {@code ${createdAt}} and {@code ${tags}} are referenceable
     * from any later cell or step. Because resolution is write-through, {@code echoed}
     * above already sees {@code createdAt} even though both live in the same table -
     * directly via {@code ${createdAt}} or through a context-aware JEXL function.
     */
    @Given("I define scenario variables:")
    public void defineScenarioVariables(StepVariables stepVariables) {
        stepVariables.variables().forEach(v ->
                scenarioStore().put(v.name(), scenarioContext().resolveField(v.expression())));
    }
}
