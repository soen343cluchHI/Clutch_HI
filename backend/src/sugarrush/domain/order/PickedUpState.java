package sugarrush.domain.order;

public final class PickedUpState implements OrderState {
    @Override
    public String name() {
        return "PickedUp";
    }

    @Override
    public OrderState deliver() {
        return new DeliveredState();
    }
}
