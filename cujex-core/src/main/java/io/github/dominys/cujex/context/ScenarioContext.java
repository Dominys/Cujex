package io.github.dominys.cujex.context;

import io.github.dominys.cujex.cucumber.EditFieldsRecord;
import io.github.dominys.cujex.jexl.CucJexlFunctions;
import io.github.dominys.cujex.jexl.CujexJexlFunctions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.NameTransformer;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlFeatures;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

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
 *     <li><b>{@link #editFields}</b> - rebuilds a stored entity with selected fields
 *         overwritten, enabling "edit only these columns" steps for any model.</li>
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
 *     <li>call consumer-contributed functions, in the default namespace or under a
 *         custom prefix such as {@code ${math:max(3,7)}} (see {@link CujexJexlFunctions})</li>
 *     <li>call <b>context-aware</b> functions that receive the live
 *         {@link CujexJexlContext} (and thus this {@code ScenarioContext}) - see
 *         {@link CujexJexlContext}</li>
 * </ul>
 *
 * <p>Glue-scoped so it shares the same per-scenario lifecycle as the store it reads.
 */
@Component
@Scope("cucumber-glue")
public class ScenarioContext {

    /**
     * Matches a cell that is entirely a {@code ${ ... }} expression. Intended for use
     * with {@link java.util.regex.Matcher#matches()} (which anchors at both ends), so
     * the group captures the inner expression of a whole-cell reference.
     */
    public static final String CALCULATED_FIELD_PATTERN = "\\$\\{(.+?)\\}";
    private static final Pattern PATTERN = Pattern.compile(CALCULATED_FIELD_PATTERN);

    private final JexlEngine jexl;
    private final ScenarioContextStore scenarioContextStore;

    public ScenarioContext(ScenarioContextStore scenarioContextStore,
                           ObjectProvider<CujexJexlFunctions> functionContributors) {
        this.scenarioContextStore = scenarioContextStore;

        // Register CucJexlFunctions under the default (null) namespace so its
        // public static methods are callable directly, e.g. ${now('-PT1H')}.
        var namespaces = new HashMap<String, Object>();
        namespaces.put(null, CucJexlFunctions.class);

        // Merge in any consumer-contributed functions. A blank namespace targets the
        // default namespace (last registration wins - typically a CucJexlFunctions
        // subclass that keeps the built-ins); a prefix must be unique.
        functionContributors.orderedStream().forEach(contributor -> {
            var prefix = normalizePrefix(contributor.namespace());
            if (prefix != null && namespaces.containsKey(prefix)) {
                throw new IllegalStateException("Duplicate JEXL namespace '" + prefix + "'");
            }
            namespaces.put(prefix, contributor.functions());
        });

        this.jexl = new JexlBuilder()
                .cache(512)
                .strict(true)
                .silent(false)
                .namespaces(namespaces)
                // A namespace registered as a Class is, by default, treated as a holder
                // of static methods. Opting into namespace instantiation lets JEXL build
                // a fresh functor per evaluation and inject the live JexlContext through a
                // matching constructor, which is what makes context-aware functions work.
                .features(new JexlFeatures().namespaceInstantiation(true))
                // JEXL sandboxes method/class access by default, which would block calls
                // like now(...) and entity accessors. Test data is fully trusted here, so
                // we lift the restriction.
                .permissions(JexlPermissions.UNRESTRICTED)
                .create();
    }

    private static String normalizePrefix(String namespace) {
        return (namespace == null || namespace.isBlank()) ? null : namespace;
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
        // A CujexJexlContext carries both the stored variables and a back-reference
        // to this ScenarioContext, so context-aware functions can reach it.
        var context = new CujexJexlContext(scenarioContextStore.getAll(), this);
        return (T) expression.evaluate(context);
    }

    /**
     * Clones a stored entity, overwrites only the fields named in {@code fieldsRecord},
     * and returns the rebuilt entity. This is the model-agnostic engine behind the generic
     * "I edit {word} fields and store result in {word}" step, so a suite never needs a
     * per-entity edit step.
     *
     * <p>Editing is a JSON round-trip through the shared {@code OBJECT_MAPPER}: the entity
     * is serialized to a JSON tree, the named fields are overwritten in that tree, and the
     * tree is deserialized back into the original type. Because it reuses the very same
     * Jackson mapping a suite already relies on to build models from tables, it works for
     * <b>any</b> model the suite can construct - plain records, mutable POJOs with
     * getters/setters, or Lombok {@code @Builder}/{@code @Jacksonized} types alike - with
     * no {@code toBuilder()}/{@code build()} requirement.
     *
     * <p>Each requested column is validated up front against the type's
     * <em>deserializable</em> properties (see {@link #deserializableProperties}), so an
     * unknown name fails loudly with an {@link IllegalArgumentException}. Validating the
     * requested keys directly - rather than inferring validity from a strict read - is what
     * makes the engine robust across model shapes:
     * <ul>
     *     <li>a valid property currently omitted from the serialized tree (e.g. a
     *         {@code null} field dropped by {@code @JsonInclude(NON_NULL)}) is still
     *         editable, because it is a deserializable property even when absent;</li>
     *     <li>a read-only/derived getter (e.g. {@code getDisplayName()} with no setter)
     *         that serializes but cannot be bound back is ignored on read and never
     *         mistaken for an editable field;</li>
     *     <li>a genuine typo is rejected regardless of the model's
     *         {@code @JsonIgnoreProperties(ignoreUnknown = true)} policy, which would
     *         otherwise silently swallow it during a strict read.</li>
     * </ul>
     *
     * @param model        a stored entity (any Jackson-serializable type)
     * @param fieldsRecord the fields to overwrite (values already JEXL-resolved)
     * @return a new instance with the named fields replaced
     */
    public Object editFields(Object model, EditFieldsRecord fieldsRecord) {
        var type = model.getClass();
        if (!(OBJECT_MAPPER.valueToTree(model) instanceof ObjectNode node)) {
            throw new IllegalArgumentException(type.getSimpleName()
                    + " does not serialize to a JSON object and cannot be edited");
        }
        var editable = deserializableProperties(type);
        fieldsRecord.fields().forEach((field, value) -> {
            if (!editable.contains(field)) {
                throw new IllegalArgumentException(
                        "Field '" + field + "' not found on " + type.getSimpleName());
            }
            node.set(field, OBJECT_MAPPER.valueToTree(value));
        });
        try {
            return OBJECT_MAPPER.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to edit fields of " + type.getSimpleName(), e);
        }
    }

    /**
     * The JSON property names Jackson can <em>deserialize</em> (bind back) for {@code type}.
     * This mirrors what a round-trip read will actually accept, so it is the authoritative
     * set an edit column must belong to:
     * <ul>
     *     <li>setter-, field- and constructor/creator-bound properties (covers plain
     *         records and mutable POJOs);</li>
     *     <li>properties writable only through a configured builder - introspected via the
     *         builder, so a {@code @JsonDeserialize(builder = ...)} / {@code @Jacksonized}
     *         type whose value class exposes no mutators is still handled;</li>
     *     <li>the flattened JSON fields of a {@code @JsonUnwrapped} property (respecting any
     *         prefix/suffix), rather than the wrapper property name.</li>
     * </ul>
     * Read-only/derived getters are excluded, and the set is derived purely from the type's
     * property model, so it is independent of any
     * {@code @JsonIgnoreProperties(ignoreUnknown = true)} policy.
     */
    private static Set<String> deserializableProperties(Class<?> type) {
        var config = OBJECT_MAPPER.getDeserializationConfig();
        var names = new HashSet<String>();
        collectDeserializableNames(config, introspectForDeserialization(config, OBJECT_MAPPER.constructType(type)),
                NameTransformer.NOP, names);
        return names;
    }

    private static void collectDeserializableNames(DeserializationConfig config, BeanDescription description,
                                                   NameTransformer transform, Set<String> names) {
        var introspector = config.getAnnotationIntrospector();
        for (var property : description.findProperties()) {
            if (!property.couldDeserialize()) {
                continue;
            }
            var member = property.getPrimaryMember();
            var unwrapper = member == null ? null : introspector.findUnwrappingNameTransformer(member);
            if (unwrapper == null) {
                names.add(transform.transform(property.getName()));
            } else {
                var nested = introspectForDeserialization(config, property.getPrimaryType());
                collectDeserializableNames(config, nested, NameTransformer.chainedTransformer(transform, unwrapper),
                        names);
            }
        }
    }

    /**
     * Introspects {@code type} for deserialization, switching to the configured builder's
     * property model when the type is deserialized through a builder.
     */
    private static BeanDescription introspectForDeserialization(DeserializationConfig config, JavaType type) {
        var description = config.introspect(type);
        var builder = config.getAnnotationIntrospector().findPOJOBuilder(description.getClassInfo());
        return builder == null
                ? description
                : config.introspectForBuilder(OBJECT_MAPPER.constructType(builder), description);
    }
}
