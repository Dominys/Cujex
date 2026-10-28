package org.dominys.ittests.steps;

import io.github.dominys.cujex.cucumber.BaseCucRecord;
import io.github.dominys.cujex.cucumber.EditFieldsRecord;
import io.github.dominys.cujex.context.ScenarioContext;
import io.github.dominys.cujex.context.ScenarioContextStore;
import io.github.dominys.cujex.steps.BaseSteps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.dominys.ittests.api.client.OrderApi;
import org.dominys.ittests.api.model.Customer;
import org.dominys.ittests.api.model.Order;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps that drive the sample Order service over its REST API.
 *
 * <p>These steps talk to the service exclusively through the {@link OrderApi}
 * declarative HTTP client - they have no visibility of the service classes at
 * all - which is exactly how a real service-level suite stays decoupled from
 * the implementation under test.
 *
 * <p>The recurring pattern - and the thing worth learning here - is:
 * <ol>
 *     <li><b>prepare</b>: build entities from a table and store each under its
 *         {@code cucId} so later cells can reference them;</li>
 *     <li><b>act</b>: resolve an inline {@code {order}} reference, call the service,
 *         and store the server's response under a new key;</li>
 *     <li><b>assert</b>: resolve stored values and compare.</li>
 * </ol>
 */
public class OrderSteps extends BaseSteps {

    private final OrderApi orderApi;

    public OrderSteps(ScenarioContext scenarioContext, ScenarioContextStore scenarioStore, OrderApi orderApi) {
        super(scenarioContext, scenarioStore);
        this.orderApi = orderApi;
    }

    // --- prepare -----------------------------------------------------------

    @Given("I prepare customers:")
    public void prepareCustomers(List<BaseCucRecord<Customer>> customers) {
        customers.forEach(row -> scenarioStore().put(row.getCucId(), row.getData()));
    }

    @Given("I prepare orders:")
    public void prepareOrders(List<BaseCucRecord<Order>> orders) {
        orders.forEach(row -> scenarioStore().put(row.getCucId(), row.getData()));
    }

    // --- act ---------------------------------------------------------------

    @When("I create order {order} and store result in {word}")
    public void createOrder(Order order, String resultKey) {
        var created = orderApi.create(order);
        scenarioStore().put(resultKey, created);
    }

    @When("I read order with id {stringParam} and store result in {word}")
    public void readOrder(String id, String resultKey) {
        scenarioStore().put(resultKey, orderApi.get(id));
    }

    /**
     * Demonstrates {@link io.github.dominys.cujex.context.ScenarioContext#updateRecordBuilderFields}:
     * take a stored order, overwrite only the columns given in the edit table, and
     * store the result under a new key.
     */
    @When("I edit order {order} fields and store result in {word}")
    public void editOrder(Order order, String resultKey, EditFieldsRecord fields) {
        var edited = scenarioContext()
                .updateRecordBuilderFields(order.toBuilder(), fields)
                .build();
        scenarioStore().put(resultKey, edited);
    }

    // --- assert ------------------------------------------------------------

    @Then("I expect that order {order} has id assigned")
    public void expectIdAssigned(Order order) {
        assertThat(order.id()).as("order id").isNotBlank();
    }

    @Then("I expect that order {order} has status {stringParam}")
    public void expectStatus(Order order, String expectedStatus) {
        assertThat(order.status().name()).isEqualTo(expectedStatus);
    }

    @Then("I expect that order {order} matches {order} ignoring server fields")
    public void expectMatchesIgnoringServerFields(Order actual, Order expected) {
        assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFields("id", "status", "createdAt")
                .isEqualTo(expected);
    }
}
