package sugarrush.domain.order;

public final class PickedUpState implements OrderState {
    public String name() {
        return "PickedUp";
    }

    public OrderState deliver() {
        return new DeliveredState();
    }
}
