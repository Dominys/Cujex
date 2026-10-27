package org.dominys.ittests.api.client;

import org.dominys.ittests.api.model.Order;
import org.dominys.ittests.api.model.OrderStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import java.util.List;

/**
 * Declarative REST client for the Order service.
 *
 * <p>This is an <em>HTTP interface</em>: the methods below describe the REST
 * contract with annotations, and Spring generates a proxy that performs the
 * actual HTTP calls (see {@link OrderServiceClient}). The tests only ever see
 * this interface and the API model records - never the service implementation -
 * which is what enforces true black-box, over-the-wire testing.
 */
@HttpExchange(url = "/orders", accept = "application/json", contentType = "application/json")
public interface OrderApi {

    /** Create an order; the service assigns id/createdAt and returns the stored copy. */
    @PostExchange
    Order create(@RequestBody Order order);

    /** Fetch a single order by id (404 if it does not exist). */
    @GetExchange("/{id}")
    Order get(@PathVariable("id") String id);

    /** Transition an existing order to a new status. */
    @PutExchange("/{id}/status")
    Order updateStatus(@PathVariable("id") String id, @RequestParam("status") OrderStatus status);

    /** List every stored order. */
    @GetExchange
    List<Order> getAll();

    /** Remove all orders; used by the tests to reset state between scenarios. */
    @DeleteExchange
    void deleteAll();
}
