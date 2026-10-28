package io.github.dominys.cujex.examples.jexl;

import io.github.dominys.cujex.jexl.CucJexlFunctions;
import io.github.dominys.cujex.jexl.CujexJexlFunctions;

/**
 * Use case 1: add functions to the <b>default</b> JEXL namespace.
 *
 * <p>By extending {@link CucJexlFunctions} this holder inherits the built-ins
 * ({@code now()}, {@code uuid()}) and adds its own, all callable <em>without</em> a
 * prefix. Registered as a {@code @Bean} in
 * {@link io.github.dominys.cujex.examples.harness.TestConfig}, it is picked up automatically by
 * {@link io.github.dominys.cujex.context.ScenarioContext}.
 *
 * <pre>
 * Given I define scenario variables:
 *   | gross | ${taxed(100, 20)} |   # -> 120, our function
 *   | id    | ${uuid()}         |   # still works, inherited built-in
 * </pre>
 */
public class OrderJexlFunctions extends CucJexlFunctions implements CujexJexlFunctions {

    @Override
    public String namespace() {
        return ""; // blank -> default namespace, no prefix needed
    }

    @Override
    public Object functions() {
        return this; // expose this bean's public instance methods
    }

    /** {@code ${taxed(100, 20)}} -> {@code 120}: gross amount after adding a percentage. */
    public int taxed(int amount, int percentage) {
        return amount + amount * percentage / 100;
    }
}
