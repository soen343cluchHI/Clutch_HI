package sugarrush.application.courier;

import java.util.*;
import sugarrush.application.order.OrderService;
import sugarrush.domain.courier.Courier;
import sugarrush.domain.user.*;

public final class CourierService {
    public record View(int id, String userId, double x, double y, Courier.Status status, String orderId, boolean offerAccepted) {
    }

    private final Map<Integer, Courier> couriers = new TreeMap<>();
    private final OrderService orders;

    public CourierService(OrderService orders) {
        this.orders = orders;
    }

    public void add(Courier courier) {
        if (couriers.values().stream().anyMatch(c -> c.userId().equals(courier.userId())))
            throw new IllegalArgumentException("Account already has a courier");
        if (couriers.putIfAbsent(courier.id(), courier) != null)
            throw new IllegalArgumentException("Duplicate courier");
    }

    public void provision(User user) {
        if (user.role() != Role.COURIER) throw new SecurityException("Courier account required");
        if (couriers.values().stream().anyMatch(c -> c.userId().equals(user.id()))) return;
        int id = couriers.isEmpty() ? 1 : Collections.max(couriers.keySet()) + 1;
        int hash = Math.abs(user.id().hashCode() % 10000);
        add(new Courier(id, user.id(), 8 + hash % 80, 8 + hash / 100 % 80));
    }

    public Courier require(int id) {
        Courier courier = couriers.get(id);
        if (courier == null)
            throw new IllegalArgumentException("Unknown courier: " + id);
        return courier;
    }

    public Courier require(User user) {
        if (user.role() != Role.COURIER) throw new SecurityException("Courier account required");
        return couriers.values().stream().filter(c -> c.userId().equals(user.id())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No courier for this account"));
    }

    public View view(User user) {
        return view(require(user));
    }

    public List<View> all() {
        return couriers.values().stream().map(CourierService::view).toList();
    }

    public View goOnDuty(User user) {
        Courier courier = require(user);
        courier.goOnDuty();
        return view(courier);
    }

    public View goOffDuty(User user) {
        Courier courier = require(user);
        courier.goOffDuty();
        return view(courier);
    }

    public View assign(int courierId, String orderId) {
        if (!orders.require(orderId).state().equals("Ready"))
            throw new IllegalStateException("Only ready orders can be assigned");
        if (couriers.values().stream().anyMatch(c -> orderId.equals(c.orderId())))
            throw new IllegalStateException("Order already has a courier");
        Courier courier = require(courierId);
        courier.assign(orderId);
        return view(courier);
    }

    public OrderService.View offer(User user) {
        String id = require(user).orderId();
        return id == null ? null : orders.view(orders.require(id));
    }

    public View acceptOffer(User user, String orderId) {
        Courier courier = requireHandoff(user, orderId, Courier.Status.ASSIGNED, "Ready");
        courier.acceptOffer(orderId);
        return view(courier);
    }

    private Courier requireHandoff(User user, String orderId, Courier.Status status, String orderState) {
        Courier courier = require(user);
        var order = orders.require(orderId);
        if (!Objects.equals(courier.orderId(), orderId))
            throw new SecurityException("Only the assigned courier can handle this order");
        if (courier.status() != status || !order.state().equals(orderState))
            throw new IllegalStateException("Invalid handoff state");
        return courier;
    }

    public OrderService.View confirmPickup(User user, String orderId) {
        Courier courier = requireHandoff(user, orderId, Courier.Status.ASSIGNED, "Ready");
        if (!courier.offerAccepted()) throw new IllegalStateException("Accept the offer before pickup");
        OrderService.View order = orders.transition(orderId, "CONFIRM_PICKUP", null);
        courier.pickUp(orderId);
        return order;
    }

    public OrderService.View confirmDelivery(User user, String orderId) {
        Courier courier = requireHandoff(user, orderId, Courier.Status.CARRYING, "PickedUp");
        OrderService.View order = orders.transition(orderId, "CONFIRM_DELIVERY", null);
        courier.deliver(orderId);
        return order;
    }

    private static View view(Courier c) {
        return new View(c.id(), c.userId(), c.x(), c.y(), c.status(), c.orderId(), c.offerAccepted());
    }
}
