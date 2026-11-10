package sugarrush.domain.order;

public final class RefundedState implements OrderState {

    @Override
    public String name() {
        return "Refunded";
    }
}