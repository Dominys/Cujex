package org.dominys.ittests.jexl;

import io.github.dominys.cujex.jexl.CujexJexlFunctions;

/**
 * Use case 2: register a <b>custom namespace</b>.
 *
 * <p>Returning a non-blank {@link #namespace()} exposes these functions under a
 * prefix, leaving the default namespace (and its built-ins) untouched. Registered
 * as a {@code @Bean} in {@link org.dominys.ittests.harness.TestConfig}.
 *
 * <pre>
 * Given I define scenario variables:
 *   | biggest | ${math:max(3, 7)} |   # -> 7
 * </pre>
 */
public class MathJexlFunctions implements CujexJexlFunctions {

    @Override
    public String namespace() {
        return "math"; // callable as ${math:...}
    }

    @Override
    public Object functions() {
        return this; // expose this bean's public instance methods
    }

    /** {@code ${math:max(3, 7)}} -> {@code 7}. */
    public int max(int a, int b) {
        return Math.max(a, b);
    }
}
