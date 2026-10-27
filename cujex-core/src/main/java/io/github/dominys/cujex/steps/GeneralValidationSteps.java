package io.github.dominys.cujex.steps;

import com.fasterxml.jackson.core.type.TypeReference;
import io.cucumber.java.en.Then;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.dominys.cujex.cucumber.ParameterTypes.convertValue;

/**
 * Generic assertions that operate purely on values resolved from the scenario
 * context - no knowledge of the domain.
 *
 * <p>These steps capture raw {@code {word}} text and resolve it with
 * {@link io.github.dominys.cujex.context.ScenarioContext#resolveField} <em>inside</em>
 * the step. That keeps the result type flexible (collection, object, ...) instead
 * of forcing it through a fixed {@code @ParameterType} return type.
 */
public class GeneralValidationSteps extends BaseSteps {

    /**
     * Asserts that two collections (each referenced via {@code ${...}}) contain the
     * same elements, ignoring order.
     *
     * <pre>
     * Then I expect that ${created.tags()} contains only ${expectedTags}
     * </pre>
     *
     * <p>Both sides are normalised to a {@code List} first, because a JEXL array
     * literal like {@code ${[`a`,`b`]}} evaluates to a Java array while a stored
     * entity field is usually a {@code List}.
     */
    @Then("I expect that {word} contains only {word}")
    public void expectContainsOnly(String actualExpr, String expectedExpr) {
        var actual = toList(scenarioContext().resolveField(actualExpr));
        var expected = toList(scenarioContext().resolveField(expectedExpr));
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * Asserts two stored values are recursively equal (handy for round-trip checks:
     * create something, read it back, compare).
     */
    @Then("I expect that {word} equals {word}")
    public void expectEquals(String actualExpr, String expectedExpr) {
        var actual = scenarioContext().resolveField(actualExpr);
        var expected = scenarioContext().resolveField(expectedExpr);
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    private static List<Object> toList(Object value) {
        return convertValue(value, new TypeReference<>() {
        });
    }
}
