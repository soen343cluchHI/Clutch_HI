package sugarrush.application.courier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import sugarrush.application.order.OrderService;
import sugarrush.domain.courier.Courier;
import sugarrush.domain.user.*;

public final class CourierService {
    // x/y is the simulated position now; target and arrivesInMs let the dashboard animate the trip.
    public record View(int id, String userId, double x, double y, Courier.Status status, String orderId, boolean offerAccepted,
            double targetX, double targetY, long arrivesInMs) {
    }

    private final Map<Integer, Courier> couriers = new TreeMap<>();
    private final OrderService orders;
    private final Clock clock;
    private final double kmPerSecond;
    private Runnable availableHandler = () -> {};

    public void onAvailable(Runnable handler) {
        availableHandler = Objects.requireNonNull(handler);
    }

    public CourierService(OrderService orders) {
        this(orders, Clock.systemUTC(), 0);
    }

    // kmPerSecond <= 0 turns the travel simulation off: couriers jump straight to each stop.
    public CourierService(OrderService orders, Clock clock, double kmPerSecond) {
        this.orders = orders;
        this.clock = Objects.requireNonNull(clock);
        this.kmPerSecond = kmPerSecond;
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
        // New couriers start somewhere within a few kilometres of downtown.
        add(new Courier(id, user.id(), -3 + hash % 100 * 0.06, -2 + hash / 100 % 100 * 0.05));
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
        return couriers.values().stream().map(this::view).toList();
    }

    public View goOnDuty(User user) {
        Courier courier = require(user);
        courier.goOnDuty();
        availableHandler.run();
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
        courier.travelTo(orders.pickupPoint(orderId), clock.instant(), kmPerSecond);
        return view(courier);
    }

    public View transfer(int currentCourierId, int replacementCourierId, String orderId) {
        Courier current = require(currentCourierId);
        Courier replacement = require(replacementCourierId);
        if (!orders.require(orderId).state().equals("Ready"))
            throw new IllegalStateException("Only ready orders can be reassigned");
        if (current.status() != Courier.Status.ASSIGNED || !Objects.equals(current.orderId(), orderId))
            throw new IllegalStateException("Current courier does not hold this offer");
        if (currentCourierId == replacementCourierId || replacement.status() != Courier.Status.IDLE
                || replacement.orderId() != null)
            throw new IllegalStateException("Replacement courier must be different and idle");
        if (couriers.values().stream().anyMatch(c -> c.id() != currentCourierId && orderId.equals(c.orderId())))
            throw new IllegalStateException("Order has another courier");

        current.release(orderId);
        current.halt(clock.instant());
        View assigned = assign(replacementCourierId, orderId);

        availableHandler.run();
        return assigned;
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
        Courier courier = requireHandoff(
                user, orderId, Courier.Status.ASSIGNED, "Ready");

        if (!courier.offerAccepted()) { 
            throw new IllegalStateException("Accept the offer before pickup");
        }

        OrderService.View order = orders.transition(orderId, "CONFIRM_PICKUP", null);
        courier.pickUp(orderId);
        courier.stopAt(orders.pickupPoint(orderId));
        courier.travelTo(orders.dropoffPoint(orderId), clock.instant(), kmPerSecond);
        orders.recordCarrier(orderId, user);
        return order;
    }

    public OrderService.View confirmDelivery(User user, String orderId) {
        Courier courier = requireHandoff(user, orderId, Courier.Status.CARRYING, "PickedUp");
        OrderService.View order = orders.transition(orderId, "CONFIRM_DELIVERY", null);
        courier.deliver(orderId);
        courier.stopAt(orders.dropoffPoint(orderId));
        availableHandler.run();
        return order;
    }

    private View view(Courier c) {
        Instant now = clock.instant();
        Courier.Point position = c.positionAt(now);
        long remaining = c.arrived(now) ? 0 : Duration.between(now, c.arrivesAt()).toMillis();
        return new View(c.id(), c.userId(), position.x(), position.y(), c.status(), c.orderId(), c.offerAccepted(),
                c.destination().x(), c.destination().y(), remaining);
    }
}
