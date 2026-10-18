package sugarrush.domain.order;

public final class ReadyState implements OrderState {
    @Override
    public String name() {
        return "Ready";
    }

    @Override
    public OrderState pickUp() {
        return new PickedUpState();
    }
}
