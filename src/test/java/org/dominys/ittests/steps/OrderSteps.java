package org.dominys.ittests.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.dominys.ittests.infra.BaseCucRecord;
import org.dominys.ittests.infra.EditFieldsRecord;
import org.dominys.ittests.sut.Customer;
import org.dominys.ittests.sut.Order;
import org.dominys.ittests.sut.OrderService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps that drive the sample {@link OrderService}.
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

    @Autowired
    private OrderService orderService;

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
        var created = orderService.create(order);
        scenarioStore().put(resultKey, created);
    }

    @When("I read order with id {stringParam} and store result in {word}")
    public void readOrder(String id, String resultKey) {
        scenarioStore().put(resultKey, orderService.get(id));
    }

    /**
     * Demonstrates {@link org.dominys.ittests.infra.ScenarioContext#updateRecordBuilderFields}:
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
