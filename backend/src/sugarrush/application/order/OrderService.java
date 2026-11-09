package sugarrush.application.order;

import java.math.BigDecimal;
import java.util.*;
import sugarrush.application.events.OrderEventPublisher;
import sugarrush.application.pricing.PricingService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.domain.order.*;
import sugarrush.domain.restaurant.Restaurant;
import sugarrush.domain.user.*;

public final class OrderService {
    public record View(String id, String customerId, String restaurantId, List<OrderLine> lines, BigDecimal total,
            String state, String rejectionReason) {
    }

    private final Map<String, Order> orders = new LinkedHashMap<>();
    private final RestaurantService restaurants;
    private final OrderEventPublisher publisher;
    private final PricingService pricing;
    private java.util.function.Consumer<String> readyHandler = id -> {};

    public void onReady(java.util.function.Consumer<String> handler) {
        readyHandler = Objects.requireNonNull(handler);
    }

    public OrderService(RestaurantService restaurants, OrderEventPublisher publisher, PricingService pricing) {
        this.restaurants = restaurants;
        this.publisher = publisher;
        this.pricing = pricing;
    }

    public View place(User user, String restaurantId, Map<String, Integer> items) {
        Restaurant r = restaurants.require(restaurantId);
        List<OrderLine> lines = items.entrySet().stream().map(e -> new OrderLine(r.item(e.getKey()), e.getValue()))
                .toList();
        Order order = new Order(UUID.randomUUID().toString(), user.id(), r, lines);
        pricing.calculate(order.total());
        orders.put(order.id(), order);
        publisher.publish(order.placedEvent());
        return view(order);
    }

    public Order require(String id) {
        Order order = orders.get(id);
        if (order == null)
            throw new IllegalArgumentException("Unknown order: " + id);
        return order;
    }

    public List<View> visible(User user) {
        return orders.values().stream().filter(o -> switch (user.role()) {
        case CUSTOMER -> o.customerId().equals(user.id());
        case RESTAURANT -> restaurants.require(o.restaurantId()).ownerId().equals(user.id());
        case OPERATOR -> true;
        case COURIER -> false;
        }).map(this::view).toList();
    }

    public View transition(String id, String action, String reason) {
        Order order = require(id);
        Restaurant r = restaurants.require(order.restaurantId());
        OrderEvent event;
        switch (action) {
        case "CANCEL_ORDER" -> {
            event = order.cancel();
            r.queue().remove(id);
        }
        case "ACCEPT_ORDER" -> {
            event = order.accept();
            r.queue().add(id);
        }
        case "REJECT_ORDER" -> event = order.reject(reason);
        case "START_PREPARING" -> {
            r.queue().requireHead(id);
            event = order.prepare();
        }
        case "MARK_READY" -> {
            r.queue().requireHead(id);
            event = order.ready();
            r.queue().complete(id);
        }
        case "CONFIRM_PICKUP" -> event = order.pickUp();
        case "CONFIRM_DELIVERY" -> event = order.deliver();
        default -> throw new IllegalArgumentException("Unknown order action");
        }
        publisher.publish(event);
        if (action.equals("MARK_READY")) readyHandler.accept(id);
        return view(order);
    }

    public View view(Order o) {
        return new View(o.id(), o.customerId(), o.restaurantId(), o.lines(), pricing.calculate(o.total()), o.state(), o.rejectionReason());
    }
}
