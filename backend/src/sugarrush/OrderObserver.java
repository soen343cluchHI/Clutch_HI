package sugarrush;

public interface OrderObserver {
    void onEvent(OrderEvent event);
}
