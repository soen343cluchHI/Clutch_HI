package sugarrush.application.commands;

import sugarrush.application.auth.AuthenticationService;
import sugarrush.application.courier.CourierService;
import sugarrush.application.dispatch.DispatchService;
import sugarrush.application.events.*;
import sugarrush.application.map.CityMapService;
import sugarrush.application.order.OrderService;
import sugarrush.application.profile.ProfileService;
import sugarrush.application.restaurant.RestaurantService;

public final class CommandProcessor {

    private final CommandHandler first;
    private final CommandLog log;

    public CommandProcessor(AuthenticationService auth, RestaurantService restaurants, OrderService orders,
            ProfileService profiles, CourierService couriers, DispatchService dispatch, CityMapService map, EventLogger events,
            DashboardObserver dashboard, CommandLog log) {
        this.log = log;
        first = new AuthenticationHandler(auth, new AuthorizationHandler(restaurants, orders, couriers,
                new ExecutionHandler(auth, restaurants, orders, profiles, couriers, dispatch, map, events, dashboard, log)));
    }

    // Serialize in-memory commands so state, queue and event updates cannot interleave.
    public synchronized Object process(Command command) {
        try {
            Object result = first.handle(command, null);
            log.record(command.action(), true, "Executed");
            return result;
        } catch (RuntimeException failure) {
            log.record(command == null ? null : command.action(), false, failure.getMessage());
            throw failure;
        }
    }
    
}
