package io.github.dominys.cujex.cucumber;

import java.util.Map;

/**
 * Holds an arbitrary set of field-name to (already JEXL-resolved) value pairs,
 * used to patch specific fields of an existing entity.
 *
 * <p>Produced from an "edit" data table and consumed by
 * {@link ScenarioContext#updateRecordBuilderFields}.
 */
public record EditFieldsRecord(Map<String, Object> fields) {
}
