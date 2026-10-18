package sugarrush.domain.order;

public final class PlacedState implements OrderState {
    public String name() {
        return "Placed";
    }

    public OrderState cancel() {
        return new CancelledState();
    }

    public OrderState accept() {
        return new AcceptedState();
    }

    public OrderState reject() {
        return new RejectedState();
    }
}
