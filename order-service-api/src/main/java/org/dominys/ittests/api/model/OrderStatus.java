package org.dominys.ittests.api.model;

/**
 * Lifecycle states of an {@link Order}.
 *
 * <p>This is part of the sample System Under Test (SUT). It has nothing to do
 * with the test infrastructure - it only exists so the example scenarios have
 * a small, realistic domain to exercise.
 */
public enum OrderStatus {
    NEW,
    PAID,
    SHIPPED,
    CANCELLED
}
