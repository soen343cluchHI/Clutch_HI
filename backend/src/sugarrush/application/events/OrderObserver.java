package sugarrush.application.events;

import sugarrush.domain.order.OrderEvent;

public interface OrderObserver {
    void onEvent(OrderEvent event);
}
