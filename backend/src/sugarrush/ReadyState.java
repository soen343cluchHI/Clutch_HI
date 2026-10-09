package sugarrush;

// Delivery transitions are outside this phase.
public final class ReadyState implements OrderState {
    public String name() { return "Ready"; }
}
