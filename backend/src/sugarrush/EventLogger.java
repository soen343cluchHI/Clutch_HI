package sugarrush;

public final class EventLogger implements OrderObserver {
    public void onEvent(OrderEvent event) { System.out.println(event); }
}
