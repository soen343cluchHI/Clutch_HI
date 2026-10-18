package sugarrush.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import sugarrush.domain.restaurant.Restaurant;

public final class Order {
    private final String id, customerId, restaurantId;
    private final List<OrderLine> lines;
    private OrderState state = new PlacedState();
    private String rejectionReason;

    public Order(String id, String customerId, Restaurant restaurant, List<OrderLine> lines) {
        if (id == null || id.isBlank() || customerId == null || customerId.isBlank())
            throw new IllegalArgumentException("Order identity required");
        restaurant.requireOpen();
        if (lines == null || lines.isEmpty())
            throw new IllegalArgumentException("Order needs items");
        for (OrderLine line : lines)
            if (!restaurant.item(line.item().id()).equals(line.item()))
                throw new IllegalArgumentException("Items must belong to the restaurant's current menu");
        this.id = id;
        this.customerId = customerId;
        this.restaurantId = restaurant.id();
        this.lines = List.copyOf(lines);
    }

    public String id() {
        return id;
    }

    public String customerId() {
        return customerId;
    }

    public String restaurantId() {
        return restaurantId;
    }

    public String state() {
        return state.name();
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public BigDecimal total() {
        return lines.stream().map(OrderLine::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public OrderEvent placedEvent() {
        return new OrderEvent(UUID.randomUUID(), id, null, state(), Instant.now());
    }

    public OrderEvent cancel() {
        return transition(state.cancel());
    }

    public OrderEvent accept() {
        return transition(state.accept());
    }

    public OrderEvent reject(String reason) {
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Rejection reason required");
        OrderState next = state.reject();
        rejectionReason = reason.trim();
        return transition(next);
    }

    public OrderEvent prepare() {
        return transition(state.prepare());
    }

    public OrderEvent ready() {
        return transition(state.ready());
    }

    public OrderEvent pickUp() {
        return transition(state.pickUp());
    }

    public OrderEvent deliver() {
        return transition(state.deliver());
    }

    private OrderEvent transition(OrderState next) {
        OrderEvent event = new OrderEvent(UUID.randomUUID(), id, state(), next.name(), Instant.now());
        state = next;
        return event;
    }
}
