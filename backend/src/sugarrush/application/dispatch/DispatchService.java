package sugarrush.application.dispatch;

import java.util.LinkedHashSet;
import java.util.List;
import sugarrush.application.courier.CourierService;
import sugarrush.application.order.OrderService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.domain.courier.Courier;

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
        var restaurant = restaurants.require(order.restaurantId());
        CourierService.View nearest = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (var courier : couriers.all()) {
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
