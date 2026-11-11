package sugarrush.application.commands;

import sugarrush.application.auth.AuthenticationService;
import sugarrush.application.courier.CourierService;
import sugarrush.application.dispatch.DispatchService;
import sugarrush.application.events.*;
import sugarrush.application.map.CityMapService;
import sugarrush.application.order.OrderService;
import sugarrush.application.profile.ProfileService;
import sugarrush.application.restaurant.RestaurantService;
import java.util.Map;
import sugarrush.domain.order.DeliveryAddress;
import sugarrush.domain.user.User;

public final class ExecutionHandler extends CommandHandler {

    private final AuthenticationService auth;
    private final RestaurantService restaurants;
    private final OrderService orders;
    private final ProfileService profiles;
    private final CourierService couriers;
    private final DispatchService dispatch;
    private final CityMapService map;
    private final EventLogger events;
    private final DashboardObserver dashboard;
    private final CommandLog log;

    public ExecutionHandler(AuthenticationService auth, RestaurantService restaurants, OrderService orders,
            ProfileService profiles, CourierService couriers, DispatchService dispatch, CityMapService map, EventLogger events,
            DashboardObserver dashboard, CommandLog log) {
        super(null);
        this.auth = auth;
        this.restaurants = restaurants;
        this.orders = orders;
        this.profiles = profiles;
        this.couriers = couriers;
        this.dispatch = dispatch;
        this.map = map;
        this.events = events;
        this.dashboard = dashboard;
        this.log = log;
    }

    @Override
    public Object handle(Command c, User user) {
        return switch (c.action()) {
        case SESSION_INFO -> user;
        case ROLE_MENU -> Action.menu(user.role());
        case LOGOUT -> {
            auth.logout(c.sessionToken());
            yield "Logged out";
        }
        case BROWSE_RESTAURANTS -> restaurants.browse(user);
        case CREATE_RESTAURANT -> restaurants.create(user, c.reason());
        case LIST_ORDERS -> orders.visible(user);
        case VIEW_ORDER_SUMMARY -> orders.summary(c.orderId());// connects your new command to the summary retrieval method
        case PLACE_ORDER -> orders.place(user, c.restaurantId(), c.items(), deliveryAddress(c.fields()));
        case PRICE_QUOTE -> orders.quote(c.restaurantId(), c.items());
        case CITY_MAP -> map.view(user);
        case RESET_SYSTEM -> "System reset to its initial state"; // SugarRush rebuilds the world after authorization passes
        case CANCEL_ORDER, ACCEPT_ORDER, REJECT_ORDER,
                START_PREPARING, MARK_READY, REFUND_ORDER ->
            orders.transition(c.orderId(), c.action().name(), c.reason());
        case RESTAURANT_QUEUE -> restaurants.require(c.restaurantId()).queue().snapshot();
        case SET_RESTAURANT_STATUS -> {
            if (c.status() == null)
                throw new IllegalArgumentException("Status required");
            restaurants.changeStatus(c.restaurantId(), c.status());
            yield "Status updated";
        }
        case PUT_MENU_ITEM -> {
            if (c.menuItem() == null)
                throw new IllegalArgumentException("Menu item required");
            restaurants.putItem(c.restaurantId(), c.menuItem());
            yield "Menu updated";
        }
        case DELETE_MENU_ITEM -> {
            restaurants.deleteItem(c.restaurantId(), c.reason());
            yield "Menu item deleted";
        }
        case VIEW_PROFILE -> profiles.view(user);
        case UPDATE_PROFILE -> profiles.update(user, c.fields());
        case REASSIGN_ORDER -> dispatch.reassign(user, c.orderId());
        case COURIER_OFFER -> couriers.offer(user);
        case ACCEPT_DELIVERY_OFFER -> couriers.acceptOffer(user, c.orderId());
        case COURIER_STATUS -> couriers.view(user);
        case GO_ON_DUTY -> couriers.goOnDuty(user);
        case GO_OFF_DUTY -> couriers.goOffDuty(user);
        case CONFIRM_PICKUP -> couriers.confirmPickup(user, c.orderId());
        case CONFIRM_DELIVERY -> couriers.confirmDelivery(user, c.orderId());
        case DASHBOARD -> dashboard.snapshot();
        case EVENT_LOG -> events.all();
        case COMMAND_LOG -> log.all();
        case LIST_LEDGER -> orders.ledgerEntries(user);
        };
    }

    private static DeliveryAddress deliveryAddress(Map<String, String> fields) {
        if (!fields.containsKey("address")) return null;
        try {
            return new DeliveryAddress(fields.get("address"), Double.parseDouble(fields.get("addressX")),
                    Double.parseDouble(fields.get("addressY")));
        } catch (NullPointerException | NumberFormatException invalid) {
            throw new IllegalArgumentException("Delivery address needs valid map coordinates");
        }
    }
}
