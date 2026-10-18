package sugarrush.application.commands;

import java.util.*;
import sugarrush.domain.restaurant.*;

public record Command(String sessionToken, Action action, String restaurantId, String orderId,
        Map<String, Integer> items, String reason, Restaurant.Status status, MenuItem menuItem) {
   
    public Command {
        Objects.requireNonNull(action);
        items = items == null ? Map.of() : Map.copyOf(items);
    }

    public Command(String token, Action action) {
        this(token, action, null, null, null, null, null, null);
    }

    public static Command place(String token, String restaurant, Map<String, Integer> items) {
        return new Command(token, Action.PLACE_ORDER, restaurant, null, items, null, null, null);
    }

    public static Command order(String token, Action action, String order, String reason) {
        return new Command(token, action, null, order, null, reason, null, null);
    }
}
