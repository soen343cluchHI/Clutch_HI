package sugarrush;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import sugarrush.application.SugarRush;
import sugarrush.application.commands.*;
import sugarrush.application.courier.CourierService;
import sugarrush.application.dispatch.DispatchService;
import sugarrush.application.events.OrderEventPublisher;
import sugarrush.application.map.CityMapService;
import sugarrush.application.order.OrderService;
import sugarrush.application.pricing.PricingService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.domain.courier.Courier;
import sugarrush.domain.order.DeliveryAddress;
import sugarrush.domain.restaurant.*;
import sugarrush.domain.user.*;

// Courier travel simulation, delivery addresses, the live map, fare quotes and the operator reset.
public final class SimulationChecks {
    private static int checks;

    private static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-11-13T12:00:00Z");

        void advance(long millis) {
            now = now.plusMillis(millis);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    public static void main(String[] args) throws Exception {
        travel();
        delivery();
        quote();
        reset();
        System.out.println("Passed " + checks + " simulation checks.");
    }

    private static void travel() {
        Instant start = Instant.parse("2026-11-13T12:00:00Z");
        Courier courier = new Courier(1, "rider", 0, 0);
        courier.travelTo(new Courier.Point(3, 4), start, 1);
        check(courier.arrivesAt().equals(start.plusSeconds(5)), "Travel time must be distance / speed");
        Courier.Point halfway = courier.positionAt(start.plusMillis(2500));
        check(close(halfway.x(), 1.5) && close(halfway.y(), 2), "Courier must move along a straight line");
        check(!courier.arrived(start.plusSeconds(4)) && courier.arrived(start.plusSeconds(5)), "Arrival time wrong");
        check(courier.positionAt(start.plusSeconds(60)).equals(new Courier.Point(3, 4)), "Courier must stop at the target");
        courier.halt(start.plusMillis(2500));
        check(courier.positionAt(start.plusSeconds(60)).equals(halfway) && courier.arrivesAt() == null,
                "Halted courier must stay put");
        courier.travelTo(new Courier.Point(10, 10), start, 0);
        check(courier.positionAt(start).equals(new Courier.Point(10, 10)), "Speed 0 must jump to the target");
        expect(IllegalArgumentException.class, () -> new DeliveryAddress(" ", 0, 0));
        expect(IllegalArgumentException.class, () -> new DeliveryAddress("Somewhere", Double.NaN, 0));
    }

    private static void delivery() {
        ManualClock clock = new ManualClock();
        RestaurantService restaurants = new RestaurantService();
        Restaurant shop = new Restaurant("r", "owner", "Shop", 2, 0, Restaurant.Status.OPEN);
        shop.putItem(new MenuItem("i", "Cake", BigDecimal.ONE, MenuItem.Status.AVAILABLE));
        restaurants.add(shop);
        OrderService orders = new OrderService(restaurants, new OrderEventPublisher(), new PricingService(pricing()));
        CourierService couriers = new CourierService(orders, clock, 0.5);
        DispatchService dispatch = new DispatchService(orders, couriers, restaurants);
        orders.onReady(dispatch::onOrderReady);
        couriers.onAvailable(dispatch::retryWaitingOrders);
        CityMapService map = new CityMapService(new CityMapService.Origin(45.5, -73.5), 0.5, couriers, orders);

        User buyer = new User("buyer", "buyer", Role.CUSTOMER);
        User stranger = new User("stranger", "stranger", Role.CUSTOMER);
        User rider = new User("rider", "rider", Role.COURIER);
        User idle = new User("idle", "idle", Role.COURIER);
        couriers.add(new Courier(1, rider.id(), 0, 0));
        couriers.add(new Courier(2, idle.id(), 2, -2.5));
        check(map.view(buyer).couriers().isEmpty(), "Offline couriers must not appear on the map");
        check(map.view(rider).couriers().size() == 1 && map.view(rider).couriers().get(0).mine(),
                "A courier always sees their own marker");
        couriers.goOnDuty(rider);

        DeliveryAddress home = new DeliveryAddress("1455 De Maisonneuve", 2, 3);
        String id = orders.place(buyer, "r", Map.of("i", 1), home).id();
        check(orders.view(orders.require(id)).deliveryAddress().equals(home), "Order lost its delivery address");
        for (String action : List.of("ACCEPT_ORDER", "START_PREPARING", "MARK_READY"))
            orders.transition(id, action, null);

        CourierService.View assigned = couriers.view(rider);
        check(assigned.orderId().equals(id) && assigned.targetX() == 2 && assigned.targetY() == 0,
                "Assigned courier must head to the restaurant");
        check(assigned.arrivesInMs() == 4000, "Trip to the restaurant must take distance / speed");
        clock.advance(2000);
        check(close(couriers.view(rider).x(), 1), "Courier position must advance with time");

        var mine = map.view(buyer).couriers().get(0);
        check(id.equals(mine.orderId()) && !mine.mine(), "Customer must see the courier carrying their order");
        check(map.view(stranger).couriers().get(0).orderId() == null, "Other customers must not see the order id");

        couriers.acceptOffer(rider, id);
        couriers.confirmPickup(rider, id);
        CourierService.View carrying = couriers.view(rider);
        check(carrying.x() == 2 && carrying.y() == 0, "Pickup happens at the restaurant");
        check(carrying.targetX() == 2 && carrying.targetY() == 3 && carrying.arrivesInMs() == 6000,
                "Carrying courier must head to the delivery address");
        clock.advance(6000);
        couriers.confirmDelivery(rider, id);
        CourierService.View delivered = couriers.view(rider);
        check(delivered.status() == Courier.Status.IDLE && delivered.x() == 2 && delivered.y() == 3,
                "Courier must finish at the delivery address");

        // Dispatch measures from where couriers are now, not where they started.
        couriers.goOnDuty(idle);
        String next = orders.place(buyer, "r", Map.of("i", 1), new DeliveryAddress("Old Port", 0, 0)).id();
        for (String action : List.of("ACCEPT_ORDER", "START_PREPARING", "MARK_READY"))
            orders.transition(next, action, null);
        // Rider started 2 km away but is now 3 km away at the drop-off; the idle courier is 2.5 km away.
        check(next.equals(couriers.view(idle).orderId()), "Nearest courier must use its current position");
    }

    private static void quote() throws Exception {
        SugarRush app = SugarRush.fromSeed(Path.of("backend/config/demo.properties"));
        Session customer = app.login("customer", "customer-demo");
        Session staff = app.login("restaurant", "restaurant-demo");
        var small = (PricingService.Quote) app.execute(new Command(customer.token(), Action.PRICE_QUOTE, "sugar-shop",
                null, Map.of("cake", 1), null, null, null));
        check(small.subtotal().equals(new BigDecimal("8.50")) && small.smallOrderSurcharge().equals(new BigDecimal("2.00"))
                && small.total().equals(new BigDecimal("14.50")), "Small-order quote wrong");
        var large = (PricingService.Quote) app.execute(new Command(customer.token(), Action.PRICE_QUOTE, "sugar-shop",
                null, Map.of("cake", 2), null, null, null));
        check(large.smallOrderSurcharge().signum() == 0 && large.total().equals(new BigDecimal("21.00")),
                "Standard quote wrong");
        var placed = (OrderService.View) app.execute(Command.place(customer.token(), "sugar-shop", Map.of("cake", 1),
                Map.of("address", "Concordia", "addressX", "-0.91", "addressY", "-0.50")));
        check(placed.total().equals(small.total()), "Quote must match the charged total");
        expect(SecurityException.class, () -> app.execute(new Command(staff.token(), Action.PRICE_QUOTE, "sugar-shop",
                null, Map.of("cake", 1), null, null, null)));
        expect(IllegalArgumentException.class, () -> app.execute(Command.place(customer.token(), "sugar-shop",
                Map.of("cake", 1), Map.of("address", "Concordia", "addressX", "east", "addressY", "0"))));
    }

    private static void reset() throws Exception {
        SugarRush app = SugarRush.fromSeed(Path.of("backend/config/demo.properties"));
        Session customer = app.login("customer", "customer-demo");
        Session staff = app.login("restaurant", "restaurant-demo");
        Session courier = app.login("courier", "courier-demo");
        Session operator = app.login("operator", "operator-demo");
        app.execute(new Command(courier.token(), Action.GO_ON_DUTY));
        app.execute(Command.place(customer.token(), "sugar-shop", Map.of("cake", 1),
                Map.of("address", "Concordia", "addressX", "-0.91", "addressY", "-0.50")));
        app.execute(new Command(staff.token(), Action.SET_RESTAURANT_STATUS, "sugar-shop", null, null, null,
                Restaurant.Status.PAUSED, null));
        expect(SecurityException.class, () -> app.execute(new Command(customer.token(), Action.RESET_SYSTEM)));

        app.execute(new Command(operator.token(), Action.RESET_SYSTEM));
        check(((List<?>) app.execute(new Command(operator.token(), Action.LIST_ORDERS))).isEmpty(), "Reset kept orders");
        check(((List<?>) app.execute(new Command(operator.token(), Action.LIST_LEDGER))).isEmpty(), "Reset kept ledger");
        var shop = ((List<?>) app.execute(new Command(operator.token(), Action.BROWSE_RESTAURANTS))).stream()
                .map(RestaurantService.View.class::cast).filter(r -> r.id().equals("sugar-shop")).findFirst().orElseThrow();
        check(shop.status() == Restaurant.Status.OPEN && shop.queueLength() == 0, "Reset kept restaurant state");
        var rider = (CourierService.View) app.execute(new Command(courier.token(), Action.COURIER_STATUS));
        check(rider.status() == Courier.Status.OFFLINE && rider.x() == -0.91, "Reset kept courier state");
        check(app.execute(new Command(customer.token(), Action.SESSION_INFO)) instanceof User,
                "Sessions must survive the reset");
    }

    private static Properties pricing() {
        Properties pricing = new Properties();
        pricing.setProperty("pricing.deliveryFee", "3.00");
        pricing.setProperty("pricing.serviceFee", "1.00");
        pricing.setProperty("pricing.smallOrderThreshold", "15.00");
        pricing.setProperty("pricing.smallOrderSurcharge", "2.00");
        return pricing;
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1e-9;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition)
            throw new AssertionError(message);
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        checks++;
        try {
            action.run();
        } catch (RuntimeException failure) {
            if (type.isInstance(failure))
                return;
            throw failure;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
