package io.github.dominys.cujex.context;

import java.util.List;

/**
 * An ordered list of named variables defined by a scenario, where each value has
 * already been run through JEXL preprocessing.
 *
 * <p>Backs the "I define scenario variables:" step. The original source used
 * Apache Commons {@code Pair}; here a tiny {@link Variable} record keeps the
 * example dependency-free.
 */
public record StepVariables(List<Variable> variables) {

    public record Variable(String name, Object value) {
    }
}
