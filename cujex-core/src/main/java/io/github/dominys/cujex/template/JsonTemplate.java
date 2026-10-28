package io.github.dominys.cujex.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.dominys.cujex.cucumber.ParameterTypes.OBJECT_MAPPER;

/**
 * A loaded JSON stub plus the model type it produces.
 *
 * <p>Stubs are <strong>self-describing</strong>: each JSON file carries a small
 * {@value #DESCRIPTOR_FIELD} section declaring the model type it deserialises
 * into, so there is no central catalogue to keep in sync. A scenario just points
 * the {@code I load JSON stubs:} step at a file name and the loader figures out
 * the rest:
 *
 * <pre>
 * {
 *   "@descriptor": { "type": "com.example.api.model.Order" },
 *   "price": 9.99,
 *   "items": [ { "sku": "widget", "quantity": 1, "price": 9.99 } ],
 *   ...
 * }
 * </pre>
 *
 * <p>This also means two different stubs can target the <em>same</em> model type
 * (e.g. {@code order_request.json} and {@code order_bulk_request.json}) without
 * any extra wiring - add the file, declare its type, done.
 *
 * <p>A template is immutable: every call to {@link #build(Map)} starts from a
 * fresh deep copy of {@link #base()}, so the same template can be reused by many
 * rows/scenarios without them leaking changes into one another.
 *
 * @param base the parsed stub document with the descriptor removed (the "common case")
 * @param type the model type the stub deserialises into
 */
public record JsonTemplate(ObjectNode base, Class<?> type) {

    /** Meta section holding stub metadata; stripped before the model is built. */
    static final String DESCRIPTOR_FIELD = "@descriptor";
    /** Descriptor property naming the fully-qualified model type. */
    static final String TYPE_FIELD = "type";

    private static final String STUB_LOCATION = "/stubs/%s.json";

    /** Regex named group holding a segment's optional {@code [index]}. */
    private static final String INDEX_GROUP = "index";

    /**
     * Loads the stub named {@code file} from {@code src/test/resources/stubs},
     * reads its descriptor to discover the model type, and returns a reusable
     * template with the descriptor stripped out.
     *
     * @param file stub file name without extension (e.g. {@code order_request})
     */
    public static JsonTemplate load(String file) {
        var resource = STUB_LOCATION.formatted(file);
        try (InputStream in = JsonTemplate.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("JSON stub resource not found on classpath: " + resource);
            }
            if (!(OBJECT_MAPPER.readTree(in) instanceof ObjectNode document)) {
                throw new IllegalStateException("JSON stub must be a JSON object: " + resource);
            }
            var type = readType(document, resource);
            document.remove(DESCRIPTOR_FIELD);
            return new JsonTemplate(document, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JSON stub " + resource, e);
        }
    }

    /**
     * Builds a model from the stub, applying only the given overrides.
     *
     * <p>Keys may be simple field names ({@code price}), dotted paths into nested
     * objects ({@code customer.country}), or indexed paths into nested arrays
     * ({@code items[0].quantity}); missing intermediate objects, arrays and
     * elements are created on the fly. A {@code null} value writes an explicit
     * JSON {@code null} (so a scenario can clear an inherited field). Any field
     * not mentioned keeps the stub's value - which is exactly what keeps scenarios
     * focused on the handful of fields that matter.
     */
    public Object build(Map<String, Object> overrides) {
        var document = base.deepCopy();
        overrides.forEach((path, value) -> setPath(document, path, OBJECT_MAPPER.valueToTree(value)));
        return OBJECT_MAPPER.convertValue(document, type);
    }

    /** Number of elements the stub defines at the given (possibly dotted) array path, or 0 if none. */
    public int collectionSize(String path) {
        return arrayAt(path) instanceof ArrayNode array ? array.size() : 0;
    }

    /**
     * Index of the first stub element at {@code path} whose {@code field} equals
     * {@code value}, or {@code -1} if the path is not an array or nothing matches.
     */
    public int matchIndex(String path, String field, String value) {
        if (arrayAt(path) instanceof ArrayNode array) {
            for (var i = 0; i < array.size(); i++) {
                if (array.get(i) instanceof ObjectNode element
                        && element.get(field) != null
                        && element.get(field).asText().equals(value)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private JsonNode arrayAt(String path) {
        JsonNode node = base;
        for (var part : path.split("\\.")) {
            if (node == null) {
                return null;
            }
            var matcher = SEGMENT.matcher(part);
            if (!matcher.matches()) {
                return null;
            }
            node = node.get(matcher.group("name"));
            if (matcher.group(INDEX_GROUP) != null) {
                if (!(node instanceof ArrayNode array)) {
                    return null;
                }
                var index = Integer.parseInt(matcher.group(INDEX_GROUP));
                node = index < array.size() ? array.get(index) : null;
            }
        }
        return node;
    }

    private static Class<?> readType(ObjectNode document, String resource) {
        if (!(document.get(DESCRIPTOR_FIELD) instanceof ObjectNode descriptor)
                || descriptor.get(TYPE_FIELD) == null) {
            throw new IllegalStateException(
                    "JSON stub '%s' must declare a \"%s\": { \"%s\": \"<fully-qualified class>\" } section"
                            .formatted(resource, DESCRIPTOR_FIELD, TYPE_FIELD));
        }
        var className = descriptor.get(TYPE_FIELD).asText();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "JSON stub '%s' declares unknown type '%s'".formatted(resource, className), e);
        }
    }

    /**
     * One path segment: a field {@code name} with an optional {@code [index]}
     * suffix for arrays, e.g. {@code customer} or {@code items[0]}.
     */
    private static final Pattern SEGMENT = Pattern.compile("(?<name>\\w+)(?:\\[(?<index>\\d+)])?");

    /**
     * Writes {@code value} at {@code path}, where {@code path} is a {@code .}-joined
     * list of {@link #SEGMENT segments}. All but the last segment are navigated
     * (creating any missing object, array or element), then the last one is set.
     */
    private static void setPath(ObjectNode root, String path, JsonNode value) {
        var parts = path.split("\\.");
        var node = root;
        for (var i = 0; i < parts.length - 1; i++) {
            node = descend(node, parts[i]);
        }
        assign(node, parts[parts.length - 1], value);
    }

    /** Navigates one segment deeper, returning (and creating if absent) the child object. */
    private static ObjectNode descend(ObjectNode parent, String segment) {
        var matcher = match(segment);
        var name = matcher.group("name");
        if (matcher.group(INDEX_GROUP) == null) {
            if (parent.get(name) instanceof ObjectNode child) {
                return child;
            }
            var created = OBJECT_MAPPER.createObjectNode();
            parent.set(name, created);
            return created;
        }
        var array = arrayChild(parent, name);
        var index = Integer.parseInt(matcher.group(INDEX_GROUP));
        if (element(array, index) instanceof ObjectNode child) {
            return child;
        }
        var created = OBJECT_MAPPER.createObjectNode();
        setElement(array, index, created);
        return created;
    }

    /** Sets the final segment on its parent, into either an object field or an array slot. */
    private static void assign(ObjectNode parent, String segment, JsonNode value) {
        var matcher = match(segment);
        var name = matcher.group("name");
        if (matcher.group(INDEX_GROUP) == null) {
            parent.set(name, value);
        } else {
            setElement(arrayChild(parent, name), Integer.parseInt(matcher.group(INDEX_GROUP)), value);
        }
    }

    private static Matcher match(String segment) {
        var matcher = SEGMENT.matcher(segment);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported override path segment '%s'".formatted(segment));
        }
        return matcher;
    }

    private static ArrayNode arrayChild(ObjectNode parent, String name) {
        if (parent.get(name) instanceof ArrayNode array) {
            return array;
        }
        var created = OBJECT_MAPPER.createArrayNode();
        parent.set(name, created);
        return created;
    }

    private static JsonNode element(ArrayNode array, int index) {
        return index < array.size() ? array.get(index) : null;
    }

    private static void setElement(ArrayNode array, int index, JsonNode value) {
        while (array.size() <= index) {
            array.addNull();
        }
        array.set(index, value);
    }
}
