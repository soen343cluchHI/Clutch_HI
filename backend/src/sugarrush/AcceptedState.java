package sugarrush;

public final class AcceptedState implements OrderState {
    public String name() { return "Accepted"; }
    public OrderState prepare() { return new PreparingState(); }
}
