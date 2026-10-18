package sugarrush.application.events;

import java.util.*;
import sugarrush.domain.order.OrderEvent;

public final class EventLogger implements OrderObserver {
    private final List<OrderEvent> events = new ArrayList<>();

    @Override
    public void onEvent(OrderEvent event) {
        events.add(event);
    }

    public List<OrderEvent> all() {
        return List.copyOf(events);
    }
}
