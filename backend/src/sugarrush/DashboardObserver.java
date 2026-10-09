package sugarrush;

import java.util.HashMap;
import java.util.Map;

public final class DashboardObserver implements OrderObserver {
    private final Map<String, String> latest = new HashMap<>();
    public void onEvent(OrderEvent event) { latest.put(event.orderId(), event.newState()); }
    public String latestState(String orderId) { return latest.get(orderId); }
}
