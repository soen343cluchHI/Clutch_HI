package sugarrush;

import java.util.ArrayList;
import java.util.List;

public final class OrderEventPublisher {
    private final List<OrderObserver> observers = new ArrayList<>();
    public void subscribe(OrderObserver observer) { observers.add(observer); }
    public void publish(OrderEvent event) {
        for (OrderObserver observer : List.copyOf(observers)) {
            try { observer.onEvent(event); }
            catch (RuntimeException failure) {
                // A failed observer must not prevent the remaining observers from receiving the event.
                System.err.println("Observer failed for " + event.eventId() + ": " + failure.getMessage());
            }
        }
    }
}
