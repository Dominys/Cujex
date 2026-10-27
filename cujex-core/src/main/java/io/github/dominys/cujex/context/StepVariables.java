package io.github.dominys.cujex.context;

import java.util.List;

/**
 * An ordered list of named variable definitions declared by a scenario, where each
 * value is still the <em>raw</em> cell expression (e.g. {@code ${now('-PT1H')}}).
 *
 * <p>Backs the "I define scenario variables:" step. Resolution is intentionally
 * deferred to the step so it can happen row-by-row with write-through storage: each
 * value is resolved and stored before the next row is resolved, which lets a later
 * cell reference an earlier one in the same table. The original source used Apache
 * Commons {@code Pair}; here a tiny {@link Variable} record keeps the example
 * dependency-free.
 */
public record StepVariables(List<Variable> variables) {

    public record Variable(String name, String expression) {
    }
}
