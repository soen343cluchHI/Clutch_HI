package sugarrush.domain.order;

public final class CancelledState implements OrderState {
    @Override
    public String name() {
        return "Cancelled";
    }
}
