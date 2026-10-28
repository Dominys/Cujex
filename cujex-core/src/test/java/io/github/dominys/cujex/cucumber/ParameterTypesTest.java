package io.github.dominys.cujex.cucumber;

import com.fasterxml.jackson.core.type.TypeReference;
import io.cucumber.datatable.DataTable;
import io.github.dominys.cujex.context.ScenarioContext;
import io.github.dominys.cujex.context.ScenarioContextStore;
import io.github.dominys.cujex.context.StepVariables;
import io.github.dominys.cujex.jexl.CujexJexlFunctions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.dominys.cujex.cucumber.ParameterTypes.OBJECT_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

class ParameterTypesTest {

    private final ScenarioContextStore store = new ScenarioContextStore();
    private final ParameterTypes parameterTypes = new ParameterTypes(scenarioContext());

    private ScenarioContext scenarioContext() {
        ObjectProvider<CujexJexlFunctions> none =
                new DefaultListableBeanFactory().getBeanProvider(CujexJexlFunctions.class);
        return new ScenarioContext(store, none);
    }

    @Test
    void convertsValueByClass() {
        assertThat(ParameterTypes.convertValue("5", Integer.class)).isEqualTo(5);
    }

    @Test
    void convertsValueByTypeReference() {
        assertThat(ParameterTypes.convertValue(List.of(1, 2), new TypeReference<List<String>>() {
        })).containsExactly("1", "2");
    }

    @Test
    void wrapsASingleValueAsAStringList() {
        assertThat(ParameterTypes.asStringList("solo")).containsExactly("solo");
    }

    @Test
    void configureAppliesToAndReturnsTheSharedMapper() {
        var seen = new AtomicReference<>();

        var returned = ParameterTypes.configure(seen::set);

        assertThat(returned).isSameAs(OBJECT_MAPPER);
        assertThat(seen.get()).isSameAs(OBJECT_MAPPER);
    }

    @Test
    void resolveRowResolvesEachCellAndSkipsNullValues() {
        var row = new HashMap<String, String>();
        row.put("sum", "${1 + 2}");
        row.put("plain", "literal");
        row.put("absent", null);

        var resolved = ParameterTypes.resolveRow(row, scenarioContext());

        assertThat(resolved).containsEntry("sum", 3).containsEntry("plain", "literal");
        assertThat(resolved).doesNotContainKey("absent");
    }

    @Test
    void toRecordResolvesCellsThenMapsTheRecord() {
        var row = Map.of("x", "${1 + 1}", "y", "3");

        var point = ParameterTypes.toRecord(row, scenarioContext(), new TypeReference<Point>() {
        });

        assertThat(point).isEqualTo(new Point(2, 3));
    }

    @Test
    void stringParamResolvesAgainstTheContext() {
        assertThat(parameterTypes.stringParam("${'id-' + (1 + 4)}")).isEqualTo("id-5");
    }

    @Test
    void editFieldsRecordResolvesItsCells() {
        var record = parameterTypes.editFieldsRecord(Map.of("qty", "${2 * 3}"));

        assertThat(record.fields()).containsEntry("qty", 6);
    }

    @Test
    void stepVariablesKeepsRawExpressionsInOrder() {
        var table = DataTable.create(List.of(
                List.of("first", "${now()}"),
                List.of("second", "plain")));

        var variables = parameterTypes.stepVariables(table).variables();

        assertThat(variables).containsExactly(
                new StepVariables.Variable("first", "${now()}"),
                new StepVariables.Variable("second", "plain"));
    }

    @Test
    void defaultTransformerDelegatesToTheMapper() {
        Type type = Integer.class;

        assertThat(parameterTypes.defaultTransformer("7", type)).isEqualTo(7);
    }

    public record Point(int x, int y) {
    }
}
