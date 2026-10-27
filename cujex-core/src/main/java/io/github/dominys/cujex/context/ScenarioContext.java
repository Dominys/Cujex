package io.github.dominys.cujex.context;

import io.github.dominys.cujex.cucumber.EditFieldsRecord;
import io.github.dominys.cujex.jexl.CucJexlFunctions;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static io.github.dominys.cujex.cucumber.ParameterTypes.OBJECT_MAPPER;

/**
 * The preprocessing brain of the infrastructure.
 *
 * <p>It does two things:
 * <ol>
 *     <li><b>{@link #resolveField(String)}</b> - turns a raw cell value into a real
 *         Java object. If the value matches <code>${ ... }</code> it is treated as
 *         a JEXL expression and evaluated against the current
 *         {@link ScenarioContextStore}; otherwise it is returned unchanged.</li>
 *     <li><b>{@link #updateRecordBuilderFields}</b> - patches selected fields of an
 *         entity's builder reflectively, enabling "edit only these columns" steps.</li>
 * </ol>
 *
 * <h2>How {@code ${...}} resolution works</h2>
 * <pre>
 *   cell: ${created.id()}
 *          |
 *          |  strip ${ }               -> "created.id()"
 *          v
 *   JEXL expression evaluated against a MapContext backed by
 *   ScenarioContextStore.getAll(), so `created` is the object a previous
 *   step stored under key "created", and `.id()` calls its accessor.
 * </pre>
 *
 * <p>Because the whole store is the JEXL context, expressions can:
 * <ul>
 *     <li>reference stored entities: {@code ${customer1}}</li>
 *     <li>navigate them: {@code ${created.customer().country()}}</li>
 *     <li>build literals: {@code ${[`vip`,`priority`]}}, {@code ${[1,2,3]}}</li>
 *     <li>call custom functions: {@code ${now('-PT1H')}} (see {@link CucJexlFunctions})</li>
 * </ul>
 *
 * <p>Glue-scoped so it shares the same per-scenario lifecycle as the store it reads.
 */
@Component
@Scope("cucumber-glue")
public class ScenarioContext {

    /** Matches a cell that is entirely a {@code ${ ... }} expression. */
    public static final String CALCULATED_FIELD_PATTERN = "^\\$\\{(.+?)\\}$";
    private static final Pattern PATTERN = Pattern.compile(CALCULATED_FIELD_PATTERN);

    private final JexlEngine jexl;
    private final ScenarioContextStore scenarioContextStore;

    public ScenarioContext(ScenarioContextStore scenarioContextStore) {
        this.scenarioContextStore = scenarioContextStore;

        // Register CucJexlFunctions under the default (null) namespace so its
        // public static methods are callable directly, e.g. ${now('-PT1H')}.
        var functions = new HashMap<String, Object>();
        functions.put(null, CucJexlFunctions.class);

        this.jexl = new JexlBuilder()
                .cache(512)
                .strict(true)
                .silent(false)
                .namespaces(functions)
                // JEXL 3.3 sandboxes method/class access by default, which would
                // block calls like now(...) and entity accessors. Test data is fully
                // trusted here, so we lift the restriction.
                .permissions(JexlPermissions.UNRESTRICTED)
                .create();
    }

    /**
     * Resolves a single cell value.
     *
     * @return the raw string if it is not a {@code ${...}} expression, otherwise
     * the evaluated JEXL result (which may be any type, including stored entities).
     */
    @SuppressWarnings("unchecked")
    public <T> T resolveField(String path) {
        if (path == null) {
            return null;
        }
        if (!PATTERN.matcher(path).matches()) {
            return (T) path;
        }

        // Strip the leading "${" and trailing "}" before compiling.
        var expression = jexl.createExpression(path.substring(2, path.length() - 1));
        var context = new MapContext(scenarioContextStore.getAll());
        return (T) expression.evaluate(context);
    }

    /**
     * Applies each field in {@code fieldsRecord} to {@code builder} by invoking the
     * builder method whose name equals the field name.
     *
     * @param builder      a builder instance (e.g. {@code order.toBuilder()})
     * @param fieldsRecord the fields to overwrite (values already JEXL-resolved)
     * @return the same builder, for chaining a {@code build()} call
     */
    public <T> T updateRecordBuilderFields(T builder, EditFieldsRecord fieldsRecord) {
        fieldsRecord.fields().forEach((key, value) -> updateRecordBuilderField(builder, key, value));
        return builder;
    }

    private void updateRecordBuilderField(Object builder, String field, Object value) {
        try {
            var method = getBuilderSetter(builder.getClass(), field);
            var convertedValue = OBJECT_MAPPER.convertValue(value, method.getParameterTypes()[0]);
            method.invoke(builder, convertedValue);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set field '" + field + "'", e);
        }
    }

    private Method getBuilderSetter(Class<?> aClass, String name) {
        return Stream.of(aClass.getMethods())
                .filter(f -> f.getName().equals(name))
                .filter(f -> f.getParameterTypes().length == 1)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Builder method '" + name + "' not found"));
    }
}
