package sugarrush.application.events;
import java.util.*;
import sugarrush.domain.order.OrderEvent;
public final class DashboardObserver implements OrderObserver {
 private final Map<String,String> states=new LinkedHashMap<>();
 public void onEvent(OrderEvent event){states.put(event.orderId(),event.newState());}
 public Map<String,String> snapshot(){return Map.copyOf(states);}
}
