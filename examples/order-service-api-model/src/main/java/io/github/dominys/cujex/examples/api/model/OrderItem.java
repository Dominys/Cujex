package io.github.dominys.cujex.examples.api.model;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * A single line item within an {@link Order}.
 *
 * <p>Immutable record following the same Lombok pattern as the rest of the model
 * ({@link Builder @Builder(toBuilder = true)} + {@link Jacksonized @Jacksonized}),
 * so the test infra can build it from data-table rows / JSON stubs and edit single
 * fields through the generated builder.
 *
 * <p>Modelling a line item as its own type lets {@link Order#items()} be a list of
 * structured objects rather than parallel arrays, which keeps JEXL expressions
 * readable, e.g. {@code ${created.items().get(0).sku()}}.
 *
 * <p>A line item can itself be a bundle of {@link Component components}; this
 * second level of nesting is what the template DSL uses to demonstrate decomposing
 * a collection that lives inside another collection's elements.
 */
@Jacksonized
@Builder(toBuilder = true)
public record OrderItem(String sku, int quantity, double price, List<Component> components) {
}
