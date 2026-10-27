package org.dominys.ittests.steps;

import org.dominys.ittests.context.ScenarioContext;
import org.dominys.ittests.context.ScenarioContextStore;

import org.springframework.beans.factory.annotation.Autowired;

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

    @Autowired
    private ScenarioContext scenarioContext;

    @Autowired
    private ScenarioContextStore scenarioStore;

    protected ScenarioContext scenarioContext() {
        return scenarioContext;
    }

    protected ScenarioContextStore scenarioStore() {
        return scenarioStore;
    }
}
