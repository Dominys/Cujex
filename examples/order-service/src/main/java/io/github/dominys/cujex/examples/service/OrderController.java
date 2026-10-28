package io.github.dominys.cujex.examples.service;

import lombok.RequiredArgsConstructor;
import io.github.dominys.cujex.examples.api.model.Order;
import io.github.dominys.cujex.examples.api.model.OrderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * REST entry point for the Order service. Mirrors the contract described by
 * {@code OrderApi} in the api module so the declarative client and this
 * controller stay in lock-step.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@RequestBody Order order) {
        return orderService.create(order);
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable("id") String id) {
        return orderService.get(id);
    }

    @PutMapping("/{id}/status")
    public Order updateStatus(@PathVariable("id") String id, @RequestParam("status") OrderStatus status) {
        return orderService.updateStatus(id, status);
    }

    @GetMapping
    public List<Order> getAll() {
        return orderService.getAll();
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll() {
        orderService.reset();
    }

    /** Translates a missing-order lookup into a 404 instead of a 500. */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
