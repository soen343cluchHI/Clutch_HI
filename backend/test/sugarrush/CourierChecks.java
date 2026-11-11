package sugarrush;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import sugarrush.application.SugarRush;
import sugarrush.application.commands.*;
import sugarrush.application.courier.CourierService;
import sugarrush.application.events.OrderEventPublisher;
import sugarrush.application.order.OrderService;
import sugarrush.application.pricing.PricingService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.domain.courier.Courier;
import sugarrush.domain.restaurant.*;
import sugarrush.domain.user.*;

public final class CourierChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        SugarRush app = SugarRush.fromSeed(Path.of("backend/config/demo.properties"));
        Session customer = app.login("customer", "customer-demo");
        Session staff = app.login("restaurant", "restaurant-demo");
        Session courier = app.login("courier", "courier-demo");
        Session operator = app.login("operator", "operator-demo");
        CourierService.View seeded = (CourierService.View) app.execute(new Command(courier.token(), Action.COURIER_STATUS));
        check(seeded.id() == 1 && seeded.x() == -0.91 && seeded.y() == -0.50, "Seed courier missing");
        check(seeded.status() == Courier.Status.OFFLINE && seeded.orderId() == null, "Seed courier not offline");
        for (Session session : List.of(customer, staff, operator)) {
            expect(SecurityException.class, () -> app.execute(new Command(session.token(), Action.GO_ON_DUTY)));
            expect(SecurityException.class, () -> app.execute(new Command(session.token(), Action.COURIER_STATUS)));
        }
        expect(IllegalStateException.class, () -> app.execute(new Command(courier.token(), Action.GO_OFF_DUTY)));
        CourierService.View onDuty = (CourierService.View) app.execute(new Command(courier.token(), Action.GO_ON_DUTY));
        check(onDuty.status() == Courier.Status.IDLE, "Courier not idle on duty");
        expect(IllegalStateException.class, () -> app.execute(new Command(courier.token(), Action.GO_ON_DUTY)));
        OrderService.View ready = (OrderService.View) app
                .execute(Command.place(customer.token(), "sugar-shop", Map.of("cake", 1)));
        for (Action action : List.of(Action.ACCEPT_ORDER, Action.START_PREPARING, Action.MARK_READY))
            app.execute(Command.order(staff.token(), action, ready.id(), null));
        expect(IllegalStateException.class,
                () -> app.execute(Command.order(courier.token(), Action.CONFIRM_PICKUP, ready.id(), null)));
        expect(SecurityException.class,
                () -> app.execute(Command.order(customer.token(), Action.CONFIRM_PICKUP, ready.id(), null)));
        expect(IllegalArgumentException.class,
                () -> app.execute(Command.order(courier.token(), Action.CONFIRM_PICKUP, "missing", null)));
        check(((OrderService.View) app.execute(new Command(courier.token(), Action.COURIER_OFFER))).id().equals(ready.id()),
                "Ready order not offered automatically");
        for (Action action : List.of(Action.ACCEPT_DELIVERY_OFFER, Action.CONFIRM_PICKUP, Action.CONFIRM_DELIVERY))
            app.execute(Command.order(courier.token(), action, ready.id(), null));
        CourierService.View offDuty = (CourierService.View) app.execute(new Command(courier.token(), Action.GO_OFF_DUTY));
        check(offDuty.status() == Courier.Status.OFFLINE, "Courier not offline off duty");
        List<String> waiting = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            var queued = (OrderService.View) app.execute(Command.place(customer.token(), "sugar-shop", Map.of("cake", 1)));
            waiting.add(queued.id());
            for (Action action : List.of(Action.ACCEPT_ORDER, Action.START_PREPARING, Action.MARK_READY))
                app.execute(Command.order(staff.token(), action, queued.id(), null));
        }
        var assigned = (CourierService.View) app.execute(new Command(courier.token(), Action.GO_ON_DUTY));
        check(waiting.get(0).equals(assigned.orderId()), "Command duty trigger did not dispatch oldest order");
        for (Action action : List.of(Action.ACCEPT_DELIVERY_OFFER, Action.CONFIRM_PICKUP, Action.CONFIRM_DELIVERY))
            app.execute(Command.order(courier.token(), action, waiting.get(0), null));
        var nextOffer = (OrderService.View) app.execute(new Command(courier.token(), Action.COURIER_OFFER));
        check(waiting.get(1).equals(nextOffer.id()) && nextOffer.state().equals("Ready"),
                "Command delivery trigger did not dispatch next waiting order");
        User registered = app.register("rider", "password", List.of(Role.COURIER));
        Session rider = app.login(registered.username(), "password");
        CourierService.View provisioned = (CourierService.View) app.execute(new Command(rider.token(), Action.COURIER_STATUS));
        check(provisioned.id() == 2 && provisioned.userId().equals(registered.id()), "Registered courier not provisioned");

        RestaurantService restaurants = new RestaurantService();
        Restaurant shop = new Restaurant("r", "owner", "Shop", 0, 0, Restaurant.Status.OPEN);
        shop.putItem(new MenuItem("i", "Cake", BigDecimal.ONE, MenuItem.Status.AVAILABLE));
        restaurants.add(shop);
        Properties pricing = new Properties();
        pricing.setProperty("pricing.deliveryFee", "3.00");
        pricing.setProperty("pricing.serviceFee", "1.00");
        pricing.setProperty("pricing.smallOrderThreshold", "15.00");
        pricing.setProperty("pricing.smallOrderSurcharge", "2.00");
        OrderService orders = new OrderService(restaurants, new OrderEventPublisher(), new PricingService(pricing));
        CourierService couriers = new CourierService(orders);
        User first = new User("courier-1", "first", Role.COURIER);
        User second = new User("courier-2", "second", Role.COURIER);
        User buyer = new User("buyer", "buyer", Role.CUSTOMER);
        couriers.add(new Courier(1, first.id(), 1, 1));
        couriers.add(new Courier(2, second.id(), 2, 2));
        expect(IllegalArgumentException.class, () -> couriers.add(new Courier(1, "courier-3", 0, 0)));
        expect(IllegalArgumentException.class, () -> couriers.add(new Courier(3, first.id(), 0, 0)));
        String order = readyOrder(orders, buyer);
        String other = readyOrder(orders, buyer);
        String placed = orders.place(buyer, "r", Map.of("i", 1)).id();
        expect(IllegalStateException.class, () -> couriers.assign(1, order));
        couriers.goOnDuty(first);
        couriers.goOnDuty(second);
        expect(IllegalStateException.class, () -> couriers.assign(1, placed));
        couriers.assign(1, order);
        check(couriers.view(first).status() == Courier.Status.ASSIGNED, "Courier not assigned");
        expect(IllegalStateException.class, () -> couriers.assign(1, other));
        expect(IllegalStateException.class, () -> couriers.assign(2, order));
        expect(IllegalStateException.class, () -> couriers.goOffDuty(first));
        expect(IllegalStateException.class, () -> couriers.confirmDelivery(first, order));
        check(orders.require(order).state().equals("Ready"), "Failed delivery changed order");
        expect(IllegalStateException.class, () -> couriers.confirmPickup(first, order));
        expect(SecurityException.class, () -> couriers.acceptOffer(second, order));
        expect(SecurityException.class, () -> couriers.confirmPickup(second, order));
        check(orders.require(order).state().equals("Ready"), "Rejected pickup changed order");
        couriers.acceptOffer(first, order);
        expect(IllegalStateException.class, () -> couriers.acceptOffer(first, order));
        check(couriers.confirmPickup(first, order).state().equals("PickedUp"), "Order not picked up");
        check(couriers.view(first).status() == Courier.Status.CARRYING, "Courier not carrying");
        expect(IllegalStateException.class, () -> couriers.goOffDuty(first));
        expect(IllegalStateException.class, () -> couriers.confirmPickup(first, order));
        check(couriers.view(first).status() == Courier.Status.CARRYING, "Failed pickup changed courier");
        check(couriers.confirmDelivery(first, order).state().equals("Delivered"), "Order not delivered");
        CourierService.View done = couriers.view(first);
        check(done.status() == Courier.Status.IDLE && done.orderId() == null, "Courier not idle after delivery");
        couriers.goOffDuty(first);
        check(couriers.all().size() == 2, "Courier list incomplete");

        Courier alone = new Courier(5, "alone", 0, 0);
        expect(IllegalArgumentException.class, () -> new Courier(0, "zero", 0, 0));
        expect(IllegalArgumentException.class, () -> new Courier(6, " ", 0, 0));
        expect(IllegalArgumentException.class, () -> new Courier(7, "nan", Double.NaN, 0));
        expect(IllegalStateException.class, () -> alone.assign("o"));
        alone.goOnDuty();
        alone.assign("o");
        expect(IllegalStateException.class, () -> alone.pickUp("wrong"));
        alone.acceptOffer("o");
        alone.pickUp("o");
        expect(IllegalStateException.class, () -> alone.deliver("wrong"));
        alone.deliver("o");
        check(alone.status() == Courier.Status.IDLE, "Courier state machine incomplete");
        System.out.println("Passed " + checks + " courier checks.");
    }

    private static String readyOrder(OrderService orders, User buyer) {
        String id = orders.place(buyer, "r", Map.of("i", 1)).id();
        for (String action : List.of("ACCEPT_ORDER", "START_PREPARING", "MARK_READY"))
            orders.transition(id, action, null);
        return id;
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
