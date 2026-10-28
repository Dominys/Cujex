package io.github.dominys.cujex.examples.api.model;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/**
 * A sub-part of an {@link OrderItem} - e.g. a single component of a bundled
 * product.
 *
 * <p>Immutable record following the same Lombok pattern as the rest of the model
 * ({@link Builder @Builder(toBuilder = true)} + {@link Jacksonized @Jacksonized}).
 *
 * <p>Its reason for existing in this example project is to give {@link OrderItem}
 * its own nested collection ({@link OrderItem#components()}), so the template DSL
 * can be shown decomposing a <em>collection within a collection</em> whose inner
 * sizes differ from one line item to the next.
 */
@Jacksonized
@Builder(toBuilder = true)
public record Component(String name, int quantity) {
}
