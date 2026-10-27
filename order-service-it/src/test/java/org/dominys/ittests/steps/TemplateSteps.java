package org.dominys.ittests.steps;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import org.dominys.ittests.template.JsonStubRef;
import org.dominys.ittests.template.JsonTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.dominys.ittests.cucumber.CucConstants.CUC_REF_ID_FIELD;

/**
 * Steps for the "mixed template" request/response model approach.
 *
 * <p>Rather than spelling out a whole model in every scenario (or storing one
 * JSON file per case), a scenario loads a shared common-case stub and then
 * overrides only the fields that drive the behaviour under test. This keeps
 * scenarios short and makes it obvious which part of the request causes the flow.
 *
 * <pre>
 * Given I load JSON stubs:
 *   | cucId     | file          |
 *   | template1 | order_request |
 *
 * Given I create models from template template1:
 *   | cucId  | customer.country | items[] | .sku    | .quantity |
 *   | order1 | DE               | [0]     | console | 2         |
 *   |        |                  | [1]     | mouse   | 1         |
 *
 * When I create order ${order1} and store result in created
 * </pre>
 */
public class TemplateSteps extends BaseSteps {

    /**
     * Loads one or more self-describing stub files and stores each as a reusable
     * {@link JsonTemplate} under its {@code cucId}. The target model type is read
     * from the stub's {@code @descriptor} section, so no central registration is
     * needed.
     */
    @Given("I load JSON stubs:")
    public void loadJsonStubs(List<JsonStubRef> stubs) {
        stubs.forEach(ref -> scenarioStore().put(ref.cucId(), JsonTemplate.load(ref.file())));
    }

    /**
     * Builds a model per table row from the named template, applying only the
     * non-{@code cucId} columns as overrides, then stores it under the row's
     * {@code cucId}.
     *
     * <p>The table is taken as a raw {@link DataTable} so each override cell can be
     * handled individually:
     * <ul>
     *     <li>a blank cell inherits the stub's value (the common case);</li>
     *     <li>{@code (NULL)} writes an explicit JSON null;</li>
     *     <li>{@code ""} writes an explicit empty string;</li>
     *     <li>anything else is JEXL-resolved first (so {@code ${...}} references,
     *         arrays, timestamps, etc. all work) exactly like the rest of the suite.</li>
     * </ul>
     * Dotted column names ({@code customer.country}) patch nested object fields,
     * and indexed ones ({@code items[0].quantity}) patch elements of nested arrays.
     *
     * <p>Headers can also be written in a <em>decomposed</em> form so a long path is
     * not repeated in every header:
     * <ul>
     *     <li>a plain header is a full root path ({@code price}, {@code customer.country});</li>
     *     <li>a header ending in {@code :} opens an <em>object scope</em>
     *         ({@code customer:}) - the following {@code .field} columns are relative
     *         to it ({@code .name} &rarr; {@code customer.name});</li>
     *     <li>a header ending in {@code []} opens a <em>collection scope</em>
     *         ({@code items[]}); the cell under it is a <em>selector</em> that fills the
     *         brackets - {@code [0]} (index), {@code [+]} (append a new element) or
     *         {@code [sku=widget]} (patch the element whose {@code sku} equals
     *         {@code widget}, appending one if none matches).</li>
     *     <li>a <em>relative</em> collection opener ({@code .components[]}) nests inside
     *         the currently open element, so {@code items[0]} + {@code .components[]} with
     *         selector {@code [1]} targets {@code items[0].components[1]} - letting one
     *         table decompose a collection that lives inside another collection's
     *         elements, each with its own size.</li>
     * </ul>
     * Every row applies all of its cells; a row with a {@code cucId} starts a new
     * model, while a row with a blank {@code cucId} keeps adding to the model above
     * it - so one table can define several models with different collection sizes.
     */
    @Given("I create models from template {word}:")
    public void createModelsFromTemplate(String templateKey, DataTable table) {
        JsonTemplate template = scenarioStore().get(templateKey);
        var rows = table.cells();
        var headers = rows.getFirst();
        var idColumn = headers.indexOf(CUC_REF_ID_FIELD);
        if (idColumn < 0) {
            throw new IllegalArgumentException(
                    "Template table must have a '%s' column".formatted(CUC_REF_ID_FIELD));
        }

        String currentId = null;
        Map<String, Object> overrides = null;
        Map<String, Integer> appendCursor = null;
        Map<String, String> scopeByColumn = null;
        for (var r = 1; r < rows.size(); r++) {
            var row = rows.get(r);
            var id = idColumn < row.size() ? row.get(idColumn) : null;
            if (!isBlank(id)) {
                flush(currentId, overrides, template);
                currentId = id;
                overrides = new LinkedHashMap<>();
                appendCursor = new HashMap<>();
                scopeByColumn = new HashMap<>();
            } else if (currentId == null) {
                throw new IllegalStateException(
                        "A nested row appears before any row with a '%s'".formatted(CUC_REF_ID_FIELD));
            }
            applyRow(template, headers, row, overrides, appendCursor, scopeByColumn);
        }
        flush(currentId, overrides, template);
    }

    private void flush(String id, Map<String, Object> overrides, JsonTemplate template) {
        if (id != null) {
            scenarioStore().put(id, template.build(overrides));
        }
    }

    /**
     * Applies every cell in one table row to {@code overrides}, tracking the active
     * scope left-to-right: a {@code x:} header activates object scope {@code x}, an
     * {@code x[]} header activates collection scope {@code x[<selector>]} using the
     * cell's selector, a {@code .child[]} header activates a collection scope nested
     * inside the active one, a {@code .field} header is taken relative to the active
     * scope, and any other header is a full root path that resets the scope.
     *
     * <p>A collection opener with a <em>blank</em> cell does not resolve a new element;
     * it re-enters the element that column resolved on an earlier row of the same
     * model (carried in {@code scopeByColumn}). That is what lets a continuation row
     * add more children to the current element without repeating its selector - a
     * non-blank selector is only needed to <em>start</em> a new element.
     */
    private void applyRow(JsonTemplate template, List<String> headers, List<String> cells,
                          Map<String, Object> overrides, Map<String, Integer> appendCursor,
                          Map<String, String> scopeByColumn) {
        String scope = null;
        for (var i = 0; i < headers.size(); i++) {
            var header = headers.get(i);
            var cell = i < cells.size() ? cells.get(i) : null;
            if (CUC_REF_ID_FIELD.equals(header)) {
                continue;
            }
            if (header.endsWith("[]")) {
                var name = header.substring(0, header.length() - 2);
                if (isBlank(cell)) {
                    scope = scopeByColumn.get(header);
                } else {
                    String collection;
                    if (name.startsWith(".")) {
                        if (scope == null) {
                            throw new IllegalStateException(
                                    "Relative collection '%s' has no open scope - precede it with a '...[]' opener"
                                            .formatted(header));
                        }
                        collection = scope + name;
                    } else {
                        collection = name;
                    }
                    scope = resolveCollectionScope(template, collection, stripBrackets(cell), overrides, appendCursor);
                    scopeByColumn.put(header, scope);
                }
            } else if (header.endsWith(":")) {
                scope = header.substring(0, header.length() - 1);
            } else if (header.startsWith(".")) {
                if (!isBlank(cell)) {
                    if (scope == null) {
                        throw new IllegalStateException(
                                "Relative column '%s' has no open scope - precede it with a '...:' or '...[]' opener"
                                        .formatted(header));
                    }
                    overrides.put(scope + header, resolveCell(cell));
                }
            } else {
                scope = null;
                if (!isBlank(cell)) {
                    overrides.put(header, resolveCell(cell));
                }
            }
        }
    }

    /**
     * Resolves a collection selector to a concrete {@code name[index]} scope,
     * seeding an append cursor from the stub's current element count so that
     * {@code [+]} and unmatched {@code [field=value]} selectors land on fresh,
     * distinct elements even across several rows of the same model.
     */
    private String resolveCollectionScope(JsonTemplate template, String collection, String selector,
                                          Map<String, Object> overrides, Map<String, Integer> appendCursor) {
        int next = appendCursor.computeIfAbsent(collection, template::collectionSize);
        int index;
        if (selector.equals("+")) {
            index = next;
            appendCursor.put(collection, next + 1);
        } else if (selector.matches("\\d+")) {
            index = Integer.parseInt(selector);
            if (index >= next) {
                appendCursor.put(collection, index + 1);
            }
        } else if (selector.indexOf('=') > 0) {
            var field = selector.substring(0, selector.indexOf('='));
            var value = selector.substring(selector.indexOf('=') + 1);
            var found = template.matchIndex(collection, field, value);
            if (found >= 0) {
                index = found;
            } else {
                index = next;
                appendCursor.put(collection, next + 1);
                overrides.put("%s[%d].%s".formatted(collection, index, field), value);
            }
        } else {
            throw new IllegalArgumentException("Unsupported element selector '[%s]'".formatted(selector));
        }
        return "%s[%d]".formatted(collection, index);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stripBrackets(String cell) {
        var trimmed = cell.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]")
                ? trimmed.substring(1, trimmed.length() - 1)
                : trimmed;
    }
}
