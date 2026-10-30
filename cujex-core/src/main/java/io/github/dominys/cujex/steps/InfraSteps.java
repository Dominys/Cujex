package io.github.dominys.cujex.steps;

import io.github.dominys.cujex.context.ScenarioContext;
import io.github.dominys.cujex.context.ScenarioContextStore;
import io.github.dominys.cujex.context.StepVariables;
import io.github.dominys.cujex.cucumber.EditFieldsRecord;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

/**
 * Generic, domain-agnostic steps for seeding and manipulating the scenario context.
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

    /**
     * Clones a stored entity, overrides only the columns named in the edit table, and
     * stores the result under a new key - the domain-agnostic counterpart of a
     * per-entity "edit" step.
     *
     * <pre>
     * When I edit ${order1} fields and store result in order2
     *   | price |
     *   | 49.95 |
     * </pre>
     *
     * The first {@code {word}} is JEXL-resolved to the stored entity; it can be any
     * Jackson-serializable model - a plain record, a mutable POJO, or a Lombok
     * {@code @Builder}/{@code @Jacksonized} type (see
     * {@link ScenarioContext#editFields}). Each table column names a JSON property and
     * its value is JEXL-resolved before being applied.
     */
    @When("I edit {word} fields and store result in {word}")
    public void editFields(String recordExpr, String resultKey, EditFieldsRecord fields) {
        var model = scenarioContext().resolveField(recordExpr);
        scenarioStore().put(resultKey, scenarioContext().editFields(model, fields));
    }
}
