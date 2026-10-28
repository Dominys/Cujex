package io.github.dominys.cujex.steps;

import io.github.dominys.cujex.context.ScenarioContext;
import io.github.dominys.cujex.context.ScenarioContextStore;

import static io.github.dominys.cujex.cucumber.CucConstants.EMPTY_PLACEHOLDER;
import static io.github.dominys.cujex.cucumber.CucConstants.NULL_PLACEHOLDER;

/**
 * Base class for step definitions, giving every step easy access to the two
 * pieces of per-scenario state:
 * <ul>
 *     <li>{@link #scenarioContext()} - JEXL resolution + builder editing.</li>
 *     <li>{@link #scenarioStore()} - the key/value store steps read from and
 *         write results into.</li>
 * </ul>
 */
public abstract class BaseSteps {

    private final ScenarioContext scenarioContext;

    private final ScenarioContextStore scenarioStore;

    protected BaseSteps(ScenarioContext scenarioContext, ScenarioContextStore scenarioStore) {
        this.scenarioContext = scenarioContext;
        this.scenarioStore = scenarioStore;
    }

    protected ScenarioContext scenarioContext() {
        return scenarioContext;
    }

    protected ScenarioContextStore scenarioStore() {
        return scenarioStore;
    }

    /**
     * Turns a single table cell into a real value the way the whole suite does:
     * the {@code (NULL)} and {@code ""} placeholders become an explicit {@code null}
     * and empty string, and anything else is JEXL-resolved (so {@code ${...}}
     * references, literals and functions all work). Shared by the template
     * creation and validation steps so a cell means the same thing whether it is
     * being written into a model or asserted against one.
     */
    protected Object resolveCell(String cell) {
        return switch (cell) {
            case NULL_PLACEHOLDER -> null;
            case EMPTY_PLACEHOLDER -> "";
            default -> scenarioContext().resolveField(cell);
        };
    }
}
