package sugarrush.domain.order;

public final class DeliveredState implements OrderState {
    @Override
    public String name() {
        return "Delivered";
    }
}
