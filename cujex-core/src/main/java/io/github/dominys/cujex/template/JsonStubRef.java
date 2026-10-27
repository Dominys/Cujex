package io.github.dominys.cujex.template;

/**
 * A single row of the {@code I load JSON stubs:} data table: bind the stub file
 * identified by {@link #file()} to the scenario-local reference {@link #cucId()}.
 *
 * <p>Jackson builds this record straight from the table columns via the suite's
 * default data-table transformer, so no dedicated {@code @DataTableType} is
 * needed.
 *
 * @param cucId scenario-local id the loaded template is stored under
 * @param file  stub file name (without extension) under {@code src/test/resources/stubs}
 *              (e.g. {@code order_request}); its model type is declared inside the file
 */
public record JsonStubRef(String cucId, String file) {
}
