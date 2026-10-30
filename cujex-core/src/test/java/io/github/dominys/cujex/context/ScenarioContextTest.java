package io.github.dominys.cujex.context;

import io.github.dominys.cujex.cucumber.EditFieldsRecord;
import io.github.dominys.cujex.jexl.CujexJexlFunctions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioContextTest {

    private final ScenarioContextStore store = new ScenarioContextStore();

    private static ObjectProvider<CujexJexlFunctions> contributors(CujexJexlFunctions... beans) {
        var factory = new DefaultListableBeanFactory();
        for (var i = 0; i < beans.length; i++) {
            factory.registerSingleton("fns" + i, beans[i]);
        }
        return factory.getBeanProvider(CujexJexlFunctions.class);
    }

    private ScenarioContext scenarioContext(CujexJexlFunctions... beans) {
        return new ScenarioContext(store, contributors(beans));
    }

    @Test
    void returnsNullUnchanged() {
        assertThat(scenarioContext().<Object>resolveField(null)).isNull();
    }

    @Test
    void returnsPlainValuesUnchanged() {
        assertThat(scenarioContext().<String>resolveField("plain")).isEqualTo("plain");
    }

    @Test
    void leavesAPartialExpressionUnresolved() {
        assertThat(scenarioContext().<String>resolveField("prefix ${x}")).isEqualTo("prefix ${x}");
    }

    @Test
    void evaluatesArithmeticExpressions() {
        assertThat(scenarioContext().<Number>resolveField("${1 + 2}").intValue()).isEqualTo(3);
    }

    @Test
    void evaluatesTheBuiltInUuidFunction() {
        String value = scenarioContext().resolveField("${uuid()}");

        assertThat(UUID.fromString(value)).isNotNull();
    }

    @Test
    void evaluatesTheBuiltInNowFunctionWithAShift() {
        var reference = Instant.now();

        Instant value = scenarioContext().resolveField("${now('-PT1H')}");

        assertThat(Duration.between(value, reference)).isCloseTo(Duration.ofHours(1), Duration.ofMinutes(1));
    }

    @Test
    void resolvesAStoredVariableByName() {
        var created = new Created("ORD-1");
        store.put("created", created);

        assertThat(scenarioContext().<Created>resolveField("${created}")).isSameAs(created);
    }

    @Test
    void navigatesIntoAStoredVariable() {
        store.put("created", new Created("ORD-1"));

        assertThat(scenarioContext().<String>resolveField("${created.id()}")).isEqualTo("ORD-1");
    }

    @Test
    void exposesAContributedPrefixedNamespace() {
        var context = scenarioContext(new MathFns());

        assertThat(context.<Number>resolveField("${math:max(3, 7)}").intValue()).isEqualTo(7);
    }

    @Test
    void rejectsDuplicateContributedNamespaces() {
        var functions = new MathFns();
        assertThatThrownBy(() -> scenarioContext(functions, functions))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate JEXL namespace 'math'");
    }

    @Test
    void editsOnlyTheNamedFieldsOfAPlainRecord() {
        var original = new Gadget("original", 1);

        var edited = scenarioContext().editFields(
                original, new EditFieldsRecord(Map.of("size", 5)));

        assertThat(edited).isEqualTo(new Gadget("original", 5));
    }

    @Test
    void editsOnlyTheNamedFieldsOfAPojo() {
        var original = new Widget();
        original.setName("original");
        original.setSize(1);

        var edited = (Widget) scenarioContext().editFields(
                original, new EditFieldsRecord(Map.of("name", "patched")));

        assertThat(edited.getName()).isEqualTo("patched");
        assertThat(edited.getSize()).isEqualTo(1);
    }

    @Test
    void failsLoudlyForAnUnknownEditField() {
        var original = new Gadget("original", 1);
        var fieldsRecord = new EditFieldsRecord(Map.of("missing", "x"));
        var context = scenarioContext();
        assertThatThrownBy(() -> context.editFields(original, fieldsRecord))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void leavesAnUntouchedInstantExactlyIntactWhenEditingAnotherField() {
        var createdAt = Instant.parse("2026-10-30T09:08:07.123456789Z");
        var original = new Dated(1, createdAt);

        var edited = (Dated) scenarioContext().editFields(
                original, new EditFieldsRecord(Map.of("size", 5)));

        assertThat(edited.size()).isEqualTo(5);
        assertThat(edited.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void editsAValidPropertyThatWasOmittedBecauseItWasNull() {
        var original = new Sparse(null);

        var edited = (Sparse) scenarioContext().editFields(
                original, new EditFieldsRecord(Map.of("name", "patched")));

        assertThat(edited.name()).isEqualTo("patched");
    }

    @Test
    void editsAWritablePropertyEvenWhenTheModelHasADerivedGetter() {
        var original = new Labeled();
        original.setName("original");

        var edited = (Labeled) scenarioContext().editFields(
                original, new EditFieldsRecord(Map.of("name", "patched")));

        assertThat(edited.getName()).isEqualTo("patched");
        assertThat(edited.getDisplayName()).isEqualTo("Mr. patched");
    }

    @Test
    void failsLoudlyForAnUnknownEditFieldEvenWhenTheModelIgnoresUnknownProperties() {
        var original = new Lenient("original");
        var fieldsRecord = new EditFieldsRecord(Map.of("naem", "typo"));
        var context = scenarioContext();
        assertThatThrownBy(() -> context.editFields(original, fieldsRecord))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("naem");
    }

    @Test
    void editsAPropertyWritableOnlyThroughAConfiguredBuilder() {
        var original = Boxed.builder().name("original").size(1).build();

        var edited = (Boxed) scenarioContext().editFields(
                original, new EditFieldsRecord(Map.of("size", 5)));

        assertThat(edited.getName()).isEqualTo("original");
        assertThat(edited.getSize()).isEqualTo(5);
    }

    @Test
    void editsAFlattenedFieldOfAnUnwrappedProperty() {
        var original = new Person("p1", new Address("Lviv", "Shevchenka"));

        var edited = (Person) scenarioContext().editFields(
                original, new EditFieldsRecord(Map.of("city", "Kyiv")));

        assertThat(edited.getId()).isEqualTo("p1");
        assertThat(edited.getAddress().city()).isEqualTo("Kyiv");
        assertThat(edited.getAddress().street()).isEqualTo("Shevchenka");
    }

    public record Created(String id) {
    }

    public record Gadget(String name, int size) {
    }

    public record Dated(int size, Instant createdAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Sparse(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lenient(String name) {
    }

    @JsonDeserialize(builder = Boxed.Builder.class)
    public static final class Boxed {
        private final String name;
        private final int size;

        private Boxed(String name, int size) {
            this.name = name;
            this.size = size;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getName() {
            return name;
        }

        public int getSize() {
            return size;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static final class Builder {
            private String name;
            private int size;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder size(int size) {
                this.size = size;
                return this;
            }

            public Boxed build() {
                return new Boxed(name, size);
            }
        }
    }

    public record Address(String city, String street) {
    }

    public static final class Person {
        private String id;
        private Address address;

        public Person() {
        }

        public Person(String id, Address address) {
            this.id = id;
            this.address = address;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @JsonUnwrapped
        public Address getAddress() {
            return address;
        }

        @JsonUnwrapped
        public void setAddress(Address address) {
            this.address = address;
        }
    }

    public static final class Labeled {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDisplayName() {
            return "Mr. " + name;
        }
    }

    public static final class Widget {
        private String name;
        private int size;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }
    }

    public static final class MathFns implements CujexJexlFunctions {
        @Override
        public String namespace() {
            return "math";
        }

        @Override
        public Object functions() {
            return this;
        }

        public int max(int a, int b) {
            return Math.max(a, b);
        }
    }
}
