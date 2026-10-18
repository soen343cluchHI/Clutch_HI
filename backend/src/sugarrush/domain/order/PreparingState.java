package sugarrush.domain.order;

public final class PreparingState implements OrderState {
    @Override
    public String name() {
        return "Preparing";
    }

    @Override
    public OrderState ready() {
        return new ReadyState();
    }

    @Override
    public OrderState cancel() {
        return new CancelledState();
    }
}
