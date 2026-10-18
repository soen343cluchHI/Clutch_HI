package sugarrush.domain.order;

public final class AcceptedState implements OrderState {
    @Override
    public String name() {
        return "Accepted";
    }

    @Override
    public OrderState prepare() {
        return new PreparingState();
    }

    @Override
    public OrderState cancel() {
        return new CancelledState();
    }
}
