package sugarrush;

import java.util.ArrayList;
import java.util.List;

// TODO: Firebase Admin RTDB persistence. This is volatile demo storage only.
public final class OrderEventRepository {
    private final List<OrderEvent> events = new ArrayList<>();
    public void save(OrderEvent event) { events.add(event); }
    public List<OrderEvent> all() { return List.copyOf(events); }
}
