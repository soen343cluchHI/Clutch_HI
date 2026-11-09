package sugarrush;

import java.math.BigDecimal;
import java.util.*;
import sugarrush.application.courier.CourierService;
import sugarrush.application.dispatch.DispatchService;
import sugarrush.application.events.OrderEventPublisher;
import sugarrush.application.order.OrderService;
import sugarrush.application.pricing.PricingService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.domain.courier.Courier;
import sugarrush.domain.restaurant.*;
import sugarrush.domain.user.*;

public final class DispatchChecks {
    private static int checks;
    private final RestaurantService restaurants = new RestaurantService();
    private final OrderService orders;
    private final CourierService couriers;
    private final DispatchService dispatch;
    private final User buyer = new User("buyer", "buyer", Role.CUSTOMER);

    private DispatchChecks() {
        Restaurant shop = new Restaurant("r", "owner", "Shop", 0, 0, Restaurant.Status.OPEN);
        shop.putItem(new MenuItem("i", "Cake", BigDecimal.ONE, MenuItem.Status.AVAILABLE));
        restaurants.add(shop);
        Properties pricing = new Properties();
        pricing.setProperty("pricing.deliveryFee", "3.00");
        pricing.setProperty("pricing.serviceFee", "1.00");
        pricing.setProperty("pricing.smallOrderThreshold", "15.00");
        pricing.setProperty("pricing.smallOrderSurcharge", "2.00");
        orders = new OrderService(restaurants, new OrderEventPublisher(), new PricingService(pricing));
        couriers = new CourierService(orders);
        dispatch = new DispatchService(orders, couriers, restaurants);
    }

    private User courier(int id, double x, double y) {
        User user = new User("courier-" + id, "rider-" + id, Role.COURIER);
        couriers.add(new Courier(id, user.id(), x, y));
        return user;
    }

    private String ready() {
        String id = orders.place(buyer, "r", Map.of("i", 1)).id();
        for (String action : List.of("ACCEPT_ORDER", "START_PREPARING", "MARK_READY"))
            orders.transition(id, action, null);
        return id;
    }

    private void selection() {
        String id = ready();
        check(dispatch.findNearestAvailableCourier(id) == null, "Empty fleet");
        User ten = courier(10, 3, 4);
        User two = courier(2, -3, -4);
        User one = courier(1, 0, 0);
        couriers.goOnDuty(ten);
        couriers.goOnDuty(two);
        check(dispatch.findNearestAvailableCourier(id).id() == 2, "Numeric tie; offline nearest excluded");
        check(couriers.view(two).orderId() == null, "Selection must not reserve");
        couriers.goOnDuty(one);
        check(dispatch.findNearestAvailableCourier(id).id() == 1, "Nearest courier");
        couriers.assign(1, id);
        check(dispatch.findNearestAvailableCourier(id) == null, "Already allocated order");
        String next = ready();
        check(dispatch.findNearestAvailableCourier(next).id() == 2, "Assigned courier excluded");
        couriers.acceptOffer(one, id);
        couriers.confirmPickup(one, id);
        check(dispatch.findNearestAvailableCourier(next).id() == 2, "Carrying courier excluded");
        String placed = orders.place(buyer, "r", Map.of("i", 1)).id();
        check(dispatch.findNearestAvailableCourier(placed) == null, "Non-ready order excluded");
    }

    private void automaticDispatch() {
        orders.onReady(dispatch::onOrderReady);
        couriers.onAvailable(dispatch::retryWaitingOrders);
        String first = ready();
        String second = ready();
        dispatch.onOrderReady(first);
        check(dispatch.waitingOrders().equals(List.of(first, second)), "Duplicate-safe FIFO");
        var snapshot = dispatch.waitingOrders();
        expect(UnsupportedOperationException.class, () -> snapshot.clear());
        check(orders.require(first).state().equals("Ready"), "Waiting order remains Ready");
        User rider = courier(1, 0, 0);
        var duty = couriers.goOnDuty(rider);
        check(duty.status() == Courier.Status.ASSIGNED && first.equals(duty.orderId()),
                "Going on duty dispatches oldest order before returning status");
        check(!duty.offerAccepted(), "Reservation must require acceptance");
        check(snapshot.equals(List.of(first, second)), "Snapshot is detached");
        check(dispatch.waitingOrders().equals(List.of(second)), "Only assigned entry removed");
        dispatch.onOrderReady(first);
        dispatch.retryWaitingOrders();
        check(dispatch.waitingOrders().equals(List.of(second)), "Allocated order is not requeued");
        expect(IllegalStateException.class, () -> couriers.goOnDuty(rider));
        expect(IllegalStateException.class, () -> couriers.confirmPickup(rider, first));
        expect(IllegalStateException.class, () -> couriers.confirmDelivery(rider, first));
        check(dispatch.waitingOrders().equals(List.of(second)), "Failed actions leave queue unchanged");
        couriers.acceptOffer(rider, first);
        couriers.confirmPickup(rider, first);
        check(dispatch.waitingOrders().equals(List.of(second)), "Pickup does not release reservation");
        check(couriers.confirmDelivery(rider, first).state().equals("Delivered"), "Delivery response preserved");
        check(second.equals(couriers.view(rider).orderId()), "Delivery immediately dispatches next order");
        check(!couriers.view(rider).offerAccepted(), "Next offer requires fresh acceptance");
        check(dispatch.waitingOrders().isEmpty(), "Queue drained");
        couriers.acceptOffer(rider, second);
        couriers.confirmPickup(rider, second);
        couriers.confirmDelivery(rider, second);
        check(couriers.view(rider).status() == Courier.Status.IDLE, "Idle when queue empty");
        String third = ready();
        check(third.equals(couriers.view(rider).orderId()), "Ready trigger assigns available courier");
        check(restaurants.require("r").queue().snapshot().isEmpty(), "Preparation completed before dispatch");
        String placed = orders.place(buyer, "r", Map.of("i", 1)).id();
        expect(IllegalStateException.class, () -> dispatch.onOrderReady(placed));
        check(dispatch.waitingOrders().isEmpty(), "Invalid notification cannot enqueue");
    }

    private void drainAndSkipStaleEntries() {
        String first = ready();
        String second = ready();
        String third = ready();
        dispatch.onOrderReady(first);
        dispatch.onOrderReady(second);
        dispatch.onOrderReady(third);
        User farther = courier(10, 3, 4);
        User nearer = courier(2, 1, 0);
        couriers.goOnDuty(farther);
        couriers.goOnDuty(nearer);
        dispatch.retryWaitingOrders();
        check(first.equals(couriers.view(nearer).orderId()), "Oldest order gets nearest available courier");
        check(second.equals(couriers.view(farther).orderId()), "Retry drains multiple orders");
        check(dispatch.waitingOrders().equals(List.of(third)), "Excess order stays queued");
        User extra = courier(3, 0, 0);
        couriers.goOnDuty(extra);
        couriers.assign(3, third);
        dispatch.retryWaitingOrders();
        check(dispatch.waitingOrders().isEmpty(), "Externally allocated entry removed");
        String stale = ready();
        dispatch.onOrderReady(stale);
        orders.transition(stale, "CONFIRM_PICKUP", null);
        dispatch.retryWaitingOrders();
        check(dispatch.waitingOrders().isEmpty(), "Non-ready entry removed");
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        checks++;
        try {
            action.run();
        } catch (RuntimeException failure) {
            if (type.isInstance(failure)) return;
            throw failure;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

    public static void main(String[] args) {
        new DispatchChecks().selection();
        new DispatchChecks().automaticDispatch();
        new DispatchChecks().drainAndSkipStaleEntries();
        System.out.println("Passed " + checks + " dispatch checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
