package sugarrush.application.events;

import java.util.*;
import sugarrush.domain.order.OrderEvent;

public final class OrderEventPublisher {
    private final List<OrderObserver> observers = new ArrayList<>();

    public void subscribe(OrderObserver observer) {
        observers.add(Objects.requireNonNull(observer));
    }

    public void publish(OrderEvent event) {
        for (OrderObserver observer : List.copyOf(observers)) {
            try {
                observer.onEvent(event);
            } catch (RuntimeException failure) {
                System.err.println("Observer failed for " + event.eventId() + ": " + failure.getMessage());
            }
        }
    }
}
