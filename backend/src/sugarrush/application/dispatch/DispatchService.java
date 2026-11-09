package sugarrush.application.dispatch;

import java.util.LinkedHashSet;
import java.util.List;
import sugarrush.application.courier.CourierService;
import sugarrush.application.order.OrderService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.domain.courier.Courier;
import sugarrush.domain.user.Role;
import sugarrush.domain.user.User;

public final class DispatchService {
    private final LinkedHashSet<String> waitingOrderIds = new LinkedHashSet<>();
    private final OrderService orders;
    private final CourierService couriers;
    private final RestaurantService restaurants;

    public DispatchService(OrderService orders, CourierService couriers, RestaurantService restaurants) {
        this.orders = orders;
        this.couriers = couriers;
        this.restaurants = restaurants;
    }

    public CourierService.View findNearestAvailableCourier(String orderId) {
        var order = orders.require(orderId);
        if (!order.state().equals("Ready") || isAllocated(orderId)) return null;
        return nearestEligibleCourier(order.restaurantId(), null);
    }

    public CourierService.View reassign(User user, String orderId) {
        if (user.role() != Role.OPERATOR) throw new SecurityException("Operator account required");
        var order = orders.require(orderId);
        if (!order.state().equals("Ready"))
            throw new IllegalStateException("Only ready orders can be reassigned");
        var current = couriers.all().stream().filter(c -> orderId.equals(c.orderId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Order has no courier to replace"));
        var replacement = nearestEligibleCourier(order.restaurantId(), current.id());
        if (replacement == null)
            throw new IllegalStateException("No replacement courier available; current assignment retained");
        return couriers.transfer(current.id(), replacement.id(), orderId);
    }

    private CourierService.View nearestEligibleCourier(String restaurantId, Integer excludedCourierId) {
        var restaurant = restaurants.require(restaurantId);
        CourierService.View nearest = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (var courier : couriers.all()) {
            if (excludedCourierId != null && courier.id() == excludedCourierId) continue;
            if (courier.status() != Courier.Status.IDLE || courier.orderId() != null) continue;
            double distance = Math.hypot(courier.x() - restaurant.x(), courier.y() - restaurant.y());
            int comparison = Double.compare(distance, bestDistance);
            if (nearest == null || comparison < 0 || (comparison == 0 && courier.id() < nearest.id())) {
                nearest = courier;
                bestDistance = distance;
            }
        }
        return nearest;
    }

    public void onOrderReady(String orderId) {
        if (!orders.require(orderId).state().equals("Ready"))
            throw new IllegalStateException("Only ready orders can enter dispatch");
        if (!isAllocated(orderId)) waitingOrderIds.add(orderId);
        retryWaitingOrders();
    }

    public void retryWaitingOrders() {
        var waiting = waitingOrderIds.iterator();
        while (waiting.hasNext()) {
            String orderId = waiting.next();
            if (!orders.require(orderId).state().equals("Ready") || isAllocated(orderId)) {
                waiting.remove();
                continue;
            }
            var courier = findNearestAvailableCourier(orderId);
            if (courier == null) return;

            couriers.assign(courier.id(), orderId);
            waiting.remove();
        }
    }

    public List<String> waitingOrders() {
        return List.copyOf(waitingOrderIds);
    }

    private boolean isAllocated(String orderId) {
        return couriers.all().stream().anyMatch(c -> orderId.equals(c.orderId()));
    }
}
