package io.github.dominys.cujex.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Unit tests for the pure template logic: the override write-path that creates
 * missing objects, arrays and elements on the fly, plus the stub-side collection
 * helpers and the self-describing {@code load} contract.
 */
class JsonTemplateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Builds against {@code JsonNode.class} so {@code build} returns the resolved tree. */
    private static JsonNode build(ObjectNode base, Map<String, Object> overrides) {
        return (JsonNode) new JsonTemplate(base, JsonNode.class).build(overrides);
    }

    private static ObjectNode object(String json) {
        try {
            return (ObjectNode) MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void overridesSimpleField() {
        var result = build(object("{\"price\":1.0}"), Map.of("price", 9.99));

        assertThat(result.at("/price").asDouble()).isEqualTo(9.99);
    }

    @Test
    void keepsUnmentionedStubFields() {
        var result = build(object("{\"price\":1.0,\"currency\":\"GBP\"}"), Map.of("price", 2.0));

        assertThat(result.at("/currency").asText()).isEqualTo("GBP");
    }

    @Test
    void createsMissingIntermediateObjectsForDottedPath() {
        var result = build(MAPPER.createObjectNode(), Map.of("customer.address.country", "UK"));

        assertThat(result.at("/customer/address/country").asText()).isEqualTo("UK");
    }

    @Test
    void createsMissingArrayAndElementForIndexedPath() {
        var result = build(MAPPER.createObjectNode(), Map.of("items[0].quantity", 3));

        assertThat(result.at("/items/0/quantity").asInt()).isEqualTo(3);
    }

    @Test
    void padsArrayWithNullsUpToIndex() {
        var result = build(MAPPER.createObjectNode(), Map.of("items[2].sku", "widget"));

        var items = result.at("/items");
        assertThat(items.size()).isEqualTo(3);
        assertThat(items.get(0).isNull()).isTrue();
        assertThat(items.get(1).isNull()).isTrue();
        assertThat(items.at("/2/sku").asText()).isEqualTo("widget");
    }

    @Test
    void writesExplicitNullToClearInheritedField() {
        var overrides = new HashMap<String, Object>();
        overrides.put("discount", null);

        var result = build(object("{\"discount\":5}"), overrides);

        assertThat(result.at("/discount").isNull()).isTrue();
    }

    @Test
    void rejectsUnsupportedPathSegment() {
        assertThatThrownBy(() -> build(MAPPER.createObjectNode(), Map.of("items[0].bad-name!", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad-name!");
    }

    @Test
    void collectionSizeCountsStubElements() {
        var template = new JsonTemplate(object("{\"items\":[{},{},{}]}"), JsonNode.class);

        assertThat(template.collectionSize("items")).isEqualTo(3);
    }

    @Test
    void collectionSizeIsZeroForMissingOrNonArrayPath() {
        var template = new JsonTemplate(object("{\"price\":1.0}"), JsonNode.class);

        assertThat(template.collectionSize("missing")).isZero();
        assertThat(template.collectionSize("price")).isZero();
    }

    @Test
    void matchIndexFindsFirstMatchingElement() {
        var template = new JsonTemplate(
                object("{\"items\":[{\"sku\":\"a\"},{\"sku\":\"b\"},{\"sku\":\"b\"}]}"), JsonNode.class);

        assertThat(template.matchIndex("items", "sku", "b")).isEqualTo(1);
    }

    @Test
    void matchIndexReturnsMinusOneWhenNothingMatchesOrPathIsNotArray() {
        var template = new JsonTemplate(
                object("{\"items\":[{\"sku\":\"a\"}],\"price\":1.0}"), JsonNode.class);

        assertThat(template.matchIndex("items", "sku", "zzz")).isEqualTo(-1);
        assertThat(template.matchIndex("price", "sku", "a")).isEqualTo(-1);
    }

    @Test
    void loadReadsDescriptorTypeAndStripsDescriptor() {
        var template = JsonTemplate.load("valid");

        assertThat(template.type()).isEqualTo(LinkedHashMap.class);
        assertThat(template.base().has("@descriptor")).isFalse();
        assertThat(template.base().at("/price").asDouble()).isEqualTo(9.99);
    }

    @Test
    void loadFailsWhenResourceMissing() {
        assertThat(catchThrowableOfType(IllegalStateException.class, () -> JsonTemplate.load("does_not_exist")))
                .hasMessageContaining("not found");
    }

    @Test
    void loadFailsWhenDescriptorMissing() {
        assertThat(catchThrowableOfType(IllegalStateException.class, () -> JsonTemplate.load("no_descriptor")))
                .hasMessageContaining("@descriptor");
    }

    @Test
    void loadFailsWhenTypeUnknown() {
        assertThat(catchThrowableOfType(IllegalStateException.class, () -> JsonTemplate.load("unknown_type")))
                .hasMessageContaining("unknown type");
    }

    @Test
    void loadFailsWhenStubIsNotAnObject() {
        assertThat(catchThrowableOfType(IllegalStateException.class, () -> JsonTemplate.load("not_object")))
                .hasMessageContaining("must be a JSON object");
    }
}
