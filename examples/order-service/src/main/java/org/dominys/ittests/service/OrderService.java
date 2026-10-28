package org.dominys.ittests.service;

import lombok.RequiredArgsConstructor;
import org.dominys.ittests.api.model.Order;
import org.dominys.ittests.api.model.OrderStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNullElse;
import static java.util.Objects.requireNonNullElseGet;

/**
 * A minimal in-memory "service" that stands in for a real, remotely deployed
 * service in these examples.
 *
 * <p>In a real service-level / e2e test suite this would be replaced by an HTTP
 * (or messaging) client pointed at a running instance. Keeping it in-memory lets
 * the example run with {@code mvn test} and no external infrastructure, while the
 * step definitions still interact with it exactly as they would with a real client:
 * send an input object, get a server-assigned result back, store it in the
 * scenario context.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final Map<String, Order> storage = new ConcurrentHashMap<>();
    private final OrderEventPublisher eventPublisher;

    /**
     * "Creates" an order: assigns a server-side id, stamps {@code createdAt},
     * defaults the status to {@link OrderStatus#NEW} when absent, and persists it.
     *
     * @return the stored order, including its generated id
     */
    public Order create(Order order) {
        var id = UUID.randomUUID().toString();
        var stored = order.toBuilder()
                .id(id)
                .status(requireNonNullElse(order.status(), OrderStatus.NEW))
                .createdAt(requireNonNullElseGet(order.createdAt(), Instant::now))
                .build();
        storage.put(id, stored);
        eventPublisher.publishOrderCreated(stored);
        return stored;
    }

    public Order get(String id) {
        var order = storage.get(id);
        if (order == null) {
            throw new NoSuchElementException("No order with id " + id);
        }
        return order;
    }

    public Order updateStatus(String id, OrderStatus status) {
        var updated = get(id).toBuilder().status(status).build();
        storage.put(id, updated);
        eventPublisher.publishOrderStatusUpdated(updated);
        return updated;
    }

    public List<Order> getAll() {
        return List.copyOf(storage.values());
    }

    public void reset() {
        storage.clear();
    }
}
