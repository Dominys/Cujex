package io.github.dominys.cujex.examples.cucumber;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.cucumber.java.DataTableType;
import io.cucumber.java.ParameterType;
import io.github.dominys.cujex.context.ScenarioContext;
import io.github.dominys.cujex.cucumber.BaseCucRecord;
import io.github.dominys.cujex.cucumber.ParameterTypes;
import io.github.dominys.cujex.examples.api.model.Customer;
import io.github.dominys.cujex.examples.api.model.Order;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/**
 * The example suite's domain glue - and the canonical demonstration of how to
 * extend Cujex with your own entities.
 *
 * <p>Cujex core ({@link ParameterTypes}) is deliberately domain-agnostic: it
 * never mentions {@link Order} or {@link Customer}. To teach the framework about
 * these types a suite adds a small glue class like this one. It is registered as
 * ordinary Cucumber glue (its package is on the glue path) and autowires the same
 * {@link ScenarioContext} the core uses, so inline params and table rows resolve
 * JEXL exactly like the built-in types.
 *
 * <p>Note this class does <em>not</em> extend {@link ParameterTypes}: Cucumber
 * would otherwise register the inherited {@code @Default*}/{@code @DataTableType}
 * methods twice and fail. Instead it reuses the public static helpers.
 *
 * <p>It also shows the third extension point - customizing the shared Jackson
 * mapper. The static initializer below calls
 * {@link ParameterTypes#configure(java.util.function.Consumer)} exactly once, when
 * Cucumber loads this glue class and before any scenario runs, to relax how blank
 * table cells map onto records suite-wide. That is the supported place for
 * suite-specific Jackson configuration (register a module, set a naming strategy,
 * enable a feature, ...).
 */
public class OrderTypes {

    static {
        // Extension point: configure the one shared ObjectMapper once, up front. Here we
        // let an empty table cell bind to a null nested object instead of failing. Do this
        // at load/setup time only - never per scenario.
        ParameterTypes.configure(mapper ->
                mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT));
    }

    @Autowired
    private ScenarioContext scenarioContext;

    // --- inline parameters: resolve {order} / {customer} references ---------

    @ParameterType(value = ".*", name = "order")
    public Order order(String s) {
        return scenarioContext.resolveField(s);
    }

    @ParameterType(value = ".*", name = "customer")
    public Customer customer(String s) {
        return scenarioContext.resolveField(s);
    }

    // --- data-table rows: build typed records via the core helper ----------

    @DataTableType
    public BaseCucRecord<Order> orderCucRecord(Map<String, String> row) {
        return ParameterTypes.toRecord(row, scenarioContext, new TypeReference<>() {
        });
    }

    @DataTableType
    public BaseCucRecord<Customer> customerCucRecord(Map<String, String> row) {
        return ParameterTypes.toRecord(row, scenarioContext, new TypeReference<>() {
        });
    }
}
