package io.github.dominys.cujex.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import io.github.dominys.cujex.context.ScenarioContext;
import io.github.dominys.cujex.context.ScenarioContextStore;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.dominys.cujex.cucumber.CucConstants.CUC_REF_ID_FIELD;
import static io.github.dominys.cujex.cucumber.ParameterTypes.OBJECT_MAPPER;
import static io.github.dominys.cujex.steps.DecompositionSupport.isBlank;
import static io.github.dominys.cujex.steps.DecompositionSupport.requireScope;
import static io.github.dominys.cujex.steps.DecompositionSupport.scopePath;
import static io.github.dominys.cujex.steps.DecompositionSupport.stripBrackets;

/**
 * Table-driven assertions that mirror the template <em>creation</em> grammar
 * (see {@link TemplateSteps}) but <em>read</em> a stored model instead of
 * building one - so a cluster of {@code I expect that ... equals ...} steps
 * collapses into a single decomposed table.
 *
 * <pre>
 * Then I expect models match:
 *   | cucId  | items[] | .sku   | .components[] | .name  | .quantity |
 *   | order1 | [0]     | laptop | [0]           | cpu    | 1         |
 *   |        |         |        | [1]           | ram    | 2         |
 *   |        | [1]     | mouse  | [0]           | sensor | 1         |
 * </pre>
 *
 * <p>The columns address the model exactly like the creation step: {@code x:}
 * opens an object scope, {@code x[]} / {@code .child[]} open (outer / nested)
 * collection scopes whose cell is a selector, {@code .field} is relative to the
 * active scope, and a blank collection cell continues the element the previous
 * row opened. The difference is semantics: here every non-blank {@code .field}
 * cell is an <em>expected</em> value asserted against the model stored under
 * {@code cucId}, not an override written into it.
 */
@SuppressWarnings("java:S5960") // assertion steps are this library's purpose, shipped in main for reuse
public class ModelValidationSteps extends BaseSteps {

    public ModelValidationSteps(ScenarioContext scenarioContext, ScenarioContextStore scenarioStore) {
        super(scenarioContext, scenarioStore);
    }

    /**
     * One path segment: a field {@code name} with an optional {@code [index]}
     * suffix for list access, e.g. {@code customer} or {@code items[0]}.
     */
    private static final Pattern SEGMENT = Pattern.compile("(?<name>\\w+)(?:\\[(?<index>\\d+)])?");

    /**
     * Asserts the listed cells against stored models, one model per {@code cucId}.
     *
     * <p>Assertions are a <em>subset</em> check: only the cells you list are
     * compared (recursively, with the actual value's type), so unlisted fields and
     * extra collection elements are ignored - mirroring how the creation table only
     * sets the fields it mentions. Collection sizes are therefore <em>not</em>
     * pinned by default; add an opt-in {@code #} column (a plain {@code items#} or a
     * relative {@code .components#}) whose cell is the expected element count when
     * you do want to assert a size.
     *
     * <p>Validation selectors are the addressing subset of the creation ones:
     * {@code [n]} (index), {@code [field=value]} (the first element whose
     * {@code field} equals {@code value}) and a blank cell (continue the current
     * element). {@code [+]} has no meaning when asserting and is rejected.
     */
    @Then("I expect models match:")
    public void expectModelsMatch(DataTable table) {
        var rows = table.cells();
        var headers = rows.getFirst();
        var idColumn = headers.indexOf(CUC_REF_ID_FIELD);
        if (idColumn < 0) {
            throw new IllegalArgumentException(
                    "Validation table must have a '%s' column".formatted(CUC_REF_ID_FIELD));
        }

        Object root = null;
        Map<String, String> scopeByColumn = null;
        for (var r = 1; r < rows.size(); r++) {
            var row = rows.get(r);
            var id = idColumn < row.size() ? row.get(idColumn) : null;
            if (!isBlank(id)) {
                root = scenarioStore().get(id);
                if (root == null) {
                    throw new IllegalStateException("No model stored under '%s'".formatted(id));
                }
                scopeByColumn = new HashMap<>();
            } else if (root == null) {
                throw new IllegalStateException(
                        "A nested row appears before any row with a '%s'".formatted(CUC_REF_ID_FIELD));
            }
            applyRow(root, headers, row, scopeByColumn);
        }
    }

    /**
     * Applies every cell in one row against {@code root}, tracking the active scope
     * left-to-right exactly like the creation step: a {@code x:} header activates
     * object scope {@code x}, an {@code x[]} / {@code .child[]} header activates a
     * (root / nested) collection scope using the cell selector, an {@code x#} header
     * asserts a collection's size, a {@code .field} header asserts a value relative
     * to the active scope, and any other header asserts a full root path.
     *
     * <p>A collection opener with a blank cell re-enters the element that column
     * resolved on an earlier row (carried in {@code scopeByColumn}), so a
     * continuation row needs no selector.
     */
    private void applyRow(Object root, List<String> headers, List<String> cells,
                          Map<String, String> scopeByColumn) {
        String scope = null;
        for (var i = 0; i < headers.size(); i++) {
            var cell = i < cells.size() ? cells.get(i) : null;
            scope = applyCell(root, headers.get(i), cell, scope, scopeByColumn);
        }
    }

    /**
     * Applies a single header/cell pair against {@code root} and returns the active
     * scope after it: collection and object openers return the scope they open,
     * size and value assertions leave the scope unchanged, and a full root-path
     * assertion clears it.
     */
    private String applyCell(Object root, String header, String cell, String scope,
                             Map<String, String> scopeByColumn) {
        if (CUC_REF_ID_FIELD.equals(header)) {
            return scope;
        }
        if (header.endsWith("[]")) {
            return applyCollectionHeader(root, header, cell, scope, scopeByColumn);
        }
        if (header.endsWith("#")) {
            if (!isBlank(cell)) {
                assertSize(root, scopePath(scope, header.substring(0, header.length() - 1), header), cell);
            }
            return scope;
        }
        if (header.endsWith(":")) {
            return header.substring(0, header.length() - 1);
        }
        if (header.startsWith(".")) {
            if (!isBlank(cell)) {
                assertValue(root, requireScope(scope, header) + header, cell);
            }
            return scope;
        }
        if (!isBlank(cell)) {
            assertValue(root, header, cell);
        }
        return null;
    }

    /**
     * Opens (or re-enters) a collection scope for an {@code x[]} / {@code .child[]}
     * header: a blank cell continues the element this column resolved on an earlier
     * row, otherwise the cell selector resolves a fresh element.
     */
    private String applyCollectionHeader(Object root, String header, String cell, String scope,
                                         Map<String, String> scopeByColumn) {
        var name = header.substring(0, header.length() - 2);
        if (isBlank(cell)) {
            return scopeByColumn.get(header);
        }
        var resolved = resolveCollectionScope(root, scopePath(scope, name, header), stripBrackets(cell));
        scopeByColumn.put(header, resolved);
        return resolved;
    }

    /**
     * Resolves a validation selector to a concrete {@code name[index]} scope by
     * looking into the <em>actual</em> list: {@code [n]} is the index itself and
     * {@code [field=value]} is the first element whose {@code field} matches.
     */
    private String resolveCollectionScope(Object root, String collection, String selector) {
        int index;
        if (selector.matches("\\d+")) {
            index = Integer.parseInt(selector);
        } else if (selector.contains("=") && !selector.startsWith("=")) {
            var eq = selector.indexOf('=');
            var field = selector.substring(0, eq);
            var value = selector.substring(eq + 1);
            index = matchIndex(asCollection(root, collection), field, value);
            if (index < 0) {
                throw new IllegalStateException(
                        "No element in '%s' has %s=%s".formatted(collection, field, value));
            }
        } else {
            throw new IllegalArgumentException(
                    "Unsupported validation selector '[%s]' (use [n] or [field=value])".formatted(selector));
        }
        return "%s[%d]".formatted(collection, index);
    }

    private void assertValue(Object root, String path, String cell) {
        var actual = navigate(root, path);
        var expected = resolveCell(cell);
        if (actual == null) {
            assertThat(expected).as(path).isNull();
            return;
        }
        var coerced = OBJECT_MAPPER.convertValue(expected, actual.getClass());
        assertThat(actual).as(path).usingRecursiveComparison().isEqualTo(coerced);
    }

    private void assertSize(Object root, String path, String cell) {
        var expected = OBJECT_MAPPER.convertValue(resolveCell(cell), Integer.class);
        assertThat(asCollection(root, path)).as("size of %s", path).hasSize(expected);
    }

    private static int matchIndex(Collection<?> collection, String field, String value) {
        var i = 0;
        for (var element : collection) {
            var actual = accessor(element, field);
            if (actual != null && actual.toString().equals(value)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static Collection<?> asCollection(Object root, String path) {
        if (navigate(root, path) instanceof Collection<?> collection) {
            return collection;
        }
        throw new IllegalStateException("Path '%s' is not a collection".formatted(path));
    }

    /**
     * Reads the value at {@code path} from {@code root} by walking each segment with
     * the model's record accessors ({@code items()}, {@code name()}, ...) and list
     * {@code get(index)} access - the reflective read-side counterpart of the
     * JSON write path used when building a model.
     */
    private static Object navigate(Object root, String path) {
        var current = root;
        for (var part : path.split("\\.")) {
            var matcher = SEGMENT.matcher(part);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Unsupported path segment '%s'".formatted(part));
            }
            current = accessor(current, matcher.group("name"));
            if (matcher.group("index") != null) {
                current = element(current, Integer.parseInt(matcher.group("index")), part);
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static Object element(Object value, int index, String segment) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException("Segment '%s' is not a list".formatted(segment));
        }
        if (index >= list.size()) {
            throw new IllegalStateException(
                    "Segment '%s' has only %d element(s)".formatted(segment, list.size()));
        }
        return list.get(index);
    }

    private static Object accessor(Object target, String name) {
        if (target == null) {
            throw new IllegalStateException("Cannot read '%s' of a null value".formatted(name));
        }
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "No accessor '%s()' on %s".formatted(name, target.getClass().getSimpleName()), e);
        }
    }
}
