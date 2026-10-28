package io.github.dominys.cujex.cucumber;

import io.github.dominys.cujex.context.ScenarioContext;
import io.github.dominys.cujex.context.StepVariables;

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
import org.springframework.context.annotation.Scope;

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
 *         records, JEXL-resolving every cell first (see {@link #resolveRow}).</li>
 * </ul>
 *
 * <h2>Extending Cujex with your own domain types</h2>
 * This class is domain-agnostic; it never mentions a concrete entity. A suite adds
 * its own glue class (not extending this one) that autowires {@link ScenarioContext}
 * and reuses the static helpers here:
 * <ul>
 *     <li>accept an entity in tables - add a {@code @DataTableType} method returning
 *         {@link #toRecord(Map, ScenarioContext, TypeReference) toRecord(row, ctx, new TypeReference&lt;&gt;(){})};</li>
 *     <li>accept it as an inline {@code {param}} - add a {@code @ParameterType} method
 *         returning {@code ctx.resolveField(s)};</li>
 *     <li>resolve an arbitrary row yourself - call
 *         {@link #resolveRow(Map, ScenarioContext) resolveRow(row, ctx)}.</li>
 * </ul>
 * See the example module's {@code OrderTypes} for a worked extension.
 */
@Scope("cucumber-glue")
public class ParameterTypes {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    private final ScenarioContext scenarioContext;

    public ParameterTypes(ScenarioContext scenarioContext) {
        this.scenarioContext = scenarioContext;
    }

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

    @ParameterType(value = ".*", name = "stringParam")
    public String stringParam(String s) {
        return scenarioContext.resolveField(s);
    }

    // ----------------------------------------------------------------------
    // Data-table types
    // ----------------------------------------------------------------------

    /**
     * Vertical two-column table of {@code name | expression} pairs. Backs
     * "I define scenario variables:".
     *
     * <p>Note this type only structures the rows; it deliberately does <em>not</em>
     * resolve the expressions. Resolution happens row-by-row in the step itself
     * (see {@code InfraSteps#defineScenarioVariables}) so that each resolved value
     * is stored before the next row is resolved. That write-through ordering lets a
     * later cell reference an earlier cell in the <em>same</em> table - directly via
     * {@code ${earlier}} or through a context-aware function - because the value is
     * already in the scenario store by the time it is needed.
     */
    @DataTableType
    public StepVariables stepVariables(DataTable dataTable) {
        return new StepVariables(IntStream.range(0, dataTable.height())
                .mapToObj(row -> new StepVariables.Variable(
                        dataTable.cell(row, 0),
                        dataTable.cell(row, 1)))
                .toList());
    }

    @DataTableType
    public EditFieldsRecord editFieldsRecord(Map<String, String> row) {
        return new EditFieldsRecord(resolveRow(row, scenarioContext));
    }

    /**
     * Converts a row into a typed {@code BaseCucRecord<T>}: resolves every cell via
     * JEXL, then lets Jackson build the record (the {@code cucId} column populates the
     * wrapper, the rest populate the unwrapped payload). This is the building block a
     * suite calls from its own {@code @DataTableType} methods:
     * <pre>{@code
     * @DataTableType
     * public BaseCucRecord<Order> orderCucRecord(Map<String, String> row) {
     *     return ParameterTypes.toRecord(row, scenarioContext, new TypeReference<>() {});
     * }
     * }</pre>
     */
    public static <T> T toRecord(Map<String, String> row, ScenarioContext ctx, TypeReference<T> reference) {
        return convertValue(resolveRow(row, ctx), reference);
    }

    /**
     * The core of per-cell preprocessing: run {@link ScenarioContext#resolveField}
     * on every non-null cell value so references, literals and function calls become
     * real objects before Jackson maps them. Exposed for custom glue that needs the
     * resolved map directly.
     */
    public static Map<String, Object> resolveRow(Map<String, String> row, ScenarioContext ctx) {
        // A plain HashMap (not Collectors.toMap) so a resolved null value is allowed.
        var resolved = new HashMap<String, Object>();
        row.forEach((key, value) -> {
            if (value != null) {
                resolved.put(key, ctx.resolveField(value));
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
