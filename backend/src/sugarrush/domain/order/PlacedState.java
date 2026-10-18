package sugarrush.domain.order;

public final class PlacedState implements OrderState {
    @Override
    public String name() {
        return "Placed";
    }

    @Override
    public OrderState cancel() {
        return new CancelledState();
    }

    @Override
    public OrderState accept() {
        return new AcceptedState();
    }

    @Override
    public OrderState reject() {
        return new RejectedState();
    }
}
