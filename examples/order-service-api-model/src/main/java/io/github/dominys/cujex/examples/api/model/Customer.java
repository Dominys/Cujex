package io.github.dominys.cujex.examples.api.model;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/**
 * A customer of the sample Order service.
 *
 * <p>Modelled as an immutable record with record-style accessors
 * ({@code id()}, {@code name()}, {@code country()}). These accessors are what
 * JEXL expressions in the feature files call, e.g. {@code ${customer1.country()}}.
 *
 * <p>Lombok generates the builder machinery the infra relies on:
 * <ul>
 *     <li>{@link Builder @Builder(toBuilder = true)} creates a {@code CustomerBuilder}
 *         with a setter per component (named exactly after the field, no prefix) plus
 *         {@code build()} and {@code toBuilder()}. The infra's
 *         {@code ScenarioContext.updateRecordBuilderFields} edits single fields by
 *         reflectively calling the setter whose name equals the field name.</li>
 *     <li>{@link Jacksonized @Jacksonized} wires Jackson to deserialize data-table
 *         rows through that builder, so JSON property {@code name} maps to builder
 *         method {@code name(..)}.</li>
 * </ul>
 */
@Jacksonized
@Builder(toBuilder = true)
public record Customer(String id, String name, String country) {
}
