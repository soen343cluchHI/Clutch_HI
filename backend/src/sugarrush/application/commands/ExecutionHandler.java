package sugarrush.application.commands;

import sugarrush.application.auth.AuthenticationService;
import sugarrush.application.events.*;
import sugarrush.application.order.OrderService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.domain.user.User;

public final class ExecutionHandler extends CommandHandler {
    
    private final AuthenticationService auth;
    private final RestaurantService restaurants;
    private final OrderService orders;
    private final EventLogger events;
    private final DashboardObserver dashboard;
    private final CommandLog log;

    public ExecutionHandler(AuthenticationService auth, RestaurantService restaurants, OrderService orders,
            EventLogger events, DashboardObserver dashboard, CommandLog log) {
        super(null);
        this.auth = auth;
        this.restaurants = restaurants;
        this.orders = orders;
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
        case LIST_ORDERS -> orders.visible(user);
        case PLACE_ORDER -> orders.place(user, c.restaurantId(), c.items());
        case CANCEL_ORDER, ACCEPT_ORDER, REJECT_ORDER, START_PREPARING, MARK_READY ->
            orders.transition(c.orderId(), c.action().name(), c.reason());
        case RESTAURANT_QUEUE -> restaurants.require(c.restaurantId()).queue().snapshot();
        case SET_RESTAURANT_STATUS -> {
            if (c.status() == null)
                throw new IllegalArgumentException("Status required");
            restaurants.require(c.restaurantId()).changeStatus(c.status());
            yield "Status updated";
        }
        case PUT_MENU_ITEM -> {
            if (c.menuItem() == null)
                throw new IllegalArgumentException("Menu item required");
            restaurants.require(c.restaurantId()).putItem(c.menuItem());
            yield "Menu updated";
        }
        case DASHBOARD -> dashboard.snapshot();
        case EVENT_LOG -> events.all();
        case COMMAND_LOG -> log.all();
        };
    }
}
