package org.dominys.ittests.cucumber;

import org.dominys.ittests.context.ScenarioContext;
import org.dominys.ittests.context.StepVariables;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.DataTableType;
import io.cucumber.java.DefaultDataTableCellTransformer;
import io.cucumber.java.DefaultDataTableEntryTransformer;
import io.cucumber.java.DefaultParameterTransformer;
import io.cucumber.java.ParameterType;
import org.dominys.ittests.api.model.Customer;
import org.dominys.ittests.api.model.Order;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Central Cucumber type registry and the bridge between feature-file text and
 * typed Java objects.
 *
 * <p>Three responsibilities:
 * <ul>
 *     <li><b>{@link #OBJECT_MAPPER}</b> - the single Jackson mapper used everywhere
 *         to convert maps/rows into records. Configure modules/features here once.</li>
 *     <li><b>{@code @ParameterType} methods</b> - convert inline step arguments like
 *         {@code {order}} by JEXL-resolving them against the scenario context.</li>
 *     <li><b>{@code @DataTableType} methods</b> - convert data-table rows into typed
 *         records, JEXL-resolving every cell first (see {@link #mapFields}).</li>
 * </ul>
 *
 * <h2>Adding support for a new type</h2>
 * To accept a new entity in tables, add one {@code @DataTableType} method that
 * delegates to {@link #convertToTypedRecord}. To accept it as an inline
 * {@code {param}}, add a {@code @ParameterType} method that returns
 * {@code scenarioContext.resolveField(s)}.
 */
public class ParameterTypes {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    @Autowired
    private ScenarioContext scenarioContext;

    public static <T> T convertValue(Object fromValue, TypeReference<T> toValueType) {
        return OBJECT_MAPPER.convertValue(fromValue, toValueType);
    }

    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        return OBJECT_MAPPER.convertValue(fromValue, toValueType);
    }

    // ----------------------------------------------------------------------
    // Inline step parameters, each JEXL-resolving the captured text against the
    // scenario context before the step method receives a typed value.
    // ----------------------------------------------------------------------

    @ParameterType(value = ".*", name = "order")
    public Order order(String s) {
        return scenarioContext.resolveField(s);
    }

    @ParameterType(value = ".*", name = "customer")
    public Customer customer(String s) {
        return scenarioContext.resolveField(s);
    }

    @ParameterType(value = ".*", name = "stringParam")
    public String stringParam(String s) {
        return scenarioContext.resolveField(s);
    }

    // ----------------------------------------------------------------------
    // Data-table types
    // ----------------------------------------------------------------------

    /**
     * Vertical two-column table of {@code name | value} pairs, with each value
     * JEXL-resolved. Backs "I define scenario variables:".
     */
    @DataTableType
    public StepVariables stepVariables(DataTable dataTable) {
        return new StepVariables(IntStream.range(0, dataTable.height())
                .mapToObj(row -> new StepVariables.Variable(
                        dataTable.cell(row, 0),
                        scenarioContext.resolveField(dataTable.cell(row, 1))))
                .toList());
    }

    @DataTableType
    public EditFieldsRecord editFieldsRecord(Map<String, String> row) {
        return new EditFieldsRecord(mapFields(row));
    }

    @DataTableType
    public BaseCucRecord<Customer> customerCucRecord(Map<String, String> row) {
        return convertToTypedRecord(row, new TypeReference<>() {
        });
    }

    @DataTableType
    public BaseCucRecord<Order> orderCucRecord(Map<String, String> row) {
        return convertToTypedRecord(row, new TypeReference<>() {
        });
    }

    /**
     * Converts a row into a {@code BaseCucRecord<T>}: resolves every cell via JEXL,
     * then lets Jackson build the record (the {@code cucId} column populates the
     * wrapper, the rest populate the unwrapped payload).
     */
    private <T> T convertToTypedRecord(Map<String, String> row, TypeReference<T> reference) {
        return convertValue(mapFields(row), reference);
    }

    /**
     * The core of per-cell preprocessing: run {@link ScenarioContext#resolveField}
     * on every non-null cell value so references, literals and function calls become
     * real objects before Jackson maps them.
     */
    private Map<String, Object> mapFields(Map<String, String> row) {
        // A plain HashMap (not Collectors.toMap) so a resolved null value is allowed.
        var resolved = new HashMap<String, Object>();
        row.forEach((key, value) -> {
            if (value != null) {
                resolved.put(key, scenarioContext.resolveField(value));
            }
        });
        return resolved;
    }

    /**
     * Fallback used by Cucumber whenever no more specific transformer matches:
     * delegate to the shared ObjectMapper.
     */
    @DefaultParameterTransformer
    @DefaultDataTableEntryTransformer
    @DefaultDataTableCellTransformer
    public Object defaultTransformer(Object fromValue, Type toValueType) {
        return OBJECT_MAPPER.convertValue(fromValue, OBJECT_MAPPER.constructType(toValueType));
    }

    /** Convenience for steps that want the raw list form of a stored collection. */
    public static List<String> asStringList(Object value) {
        return convertValue(value, new TypeReference<>() {
        });
    }
}
