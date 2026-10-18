package sugarrush.domain.order;

public final class ReadyState implements OrderState {
    public String name() {
        return "Ready";
    }

    public OrderState pickUp() {
        return new PickedUpState();
    }
}
