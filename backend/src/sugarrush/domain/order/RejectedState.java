package sugarrush.domain.order;

public final class RejectedState implements OrderState {
    @Override
    public String name() {
        return "Rejected";
    }
}
