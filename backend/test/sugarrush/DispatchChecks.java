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

    public static void main(String[] args) {
        new DispatchChecks().selection();
        System.out.println("Passed " + checks + " dispatch checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
