package sugarrush.application.commands;

import java.util.*;
import sugarrush.domain.restaurant.*;

public record Command(String sessionToken, Action action, String restaurantId, String orderId,
        Map<String, Integer> items, String reason, Restaurant.Status status, MenuItem menuItem,
        Map<String, String> fields) {

    public Command {
        Objects.requireNonNull(action);
        items = items == null ? Map.of() : Map.copyOf(items);
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public Command(String token, Action action, String restaurantId, String orderId, Map<String, Integer> items,
            String reason, Restaurant.Status status, MenuItem menuItem) {
        this(token, action, restaurantId, orderId, items, reason, status, menuItem, null);
    }

    public Command(String token, Action action) {
        this(token, action, null, null, null, null, null, null);
    }

    public static Command profile(String token, Map<String, String> fields) {
        return new Command(token, Action.UPDATE_PROFILE, null, null, null, null, null, null, fields);
    }

    public static Command place(String token, String restaurant, Map<String, Integer> items) {
        return new Command(token, Action.PLACE_ORDER, restaurant, null, items, null, null, null);
    }

    public static Command order(String token, Action action, String order, String reason) {
        return new Command(token, action, null, order, null, reason, null, null);
    }
}
