package sugarrush;

public final class PreparingState implements OrderState {
    public String name() { return "Preparing"; }
    public OrderState ready() { return new ReadyState(); }
}
