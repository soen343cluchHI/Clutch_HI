package sugarrush;

public interface OrderState {
    String name();
    default OrderState accept() { throw invalid("accept"); }
    default OrderState reject() { throw invalid("reject"); }
    default OrderState prepare() { throw invalid("prepare"); }
    default OrderState ready() { throw invalid("ready"); }
    default IllegalStateException invalid(String action) {
        return new IllegalStateException("Cannot " + action + " an order in " + name());
    }
}
