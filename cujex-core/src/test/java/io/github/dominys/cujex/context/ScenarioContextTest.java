package io.github.dominys.cujex.context;

import io.github.dominys.cujex.cucumber.EditFieldsRecord;
import io.github.dominys.cujex.jexl.CujexJexlFunctions;
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
        assertThatThrownBy(() -> scenarioContext(new MathFns(), new MathFns()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate JEXL namespace 'math'");
    }

    @Test
    void patchesOnlyTheNamedBuilderFields() {
        var builder = new GadgetBuilder().name("original").size(1);

        var returned = scenarioContext().updateRecordBuilderFields(
                builder, new EditFieldsRecord(Map.of("name", "patched", "size", 5)));

        assertThat(returned).isSameAs(builder);
        assertThat(builder.build()).isEqualTo(new Gadget("patched", 5));
    }

    @Test
    void failsLoudlyForAnUnknownBuilderField() {
        var builder = new GadgetBuilder();

        assertThatThrownBy(() -> scenarioContext().updateRecordBuilderFields(
                builder, new EditFieldsRecord(Map.of("missing", "x"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    public record Created(String id) {
    }

    public record Gadget(String name, int size) {
    }

    public static final class GadgetBuilder {
        private String name;
        private int size;

        public GadgetBuilder name(String name) {
            this.name = name;
            return this;
        }

        public GadgetBuilder size(int size) {
            this.size = size;
            return this;
        }

        public Gadget build() {
            return new Gadget(name, size);
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
