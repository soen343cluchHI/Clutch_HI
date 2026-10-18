package sugarrush.domain.order;
public final class AcceptedState implements OrderState {
 public String name(){return "Accepted";}
 public OrderState prepare(){return new PreparingState();}
 public OrderState cancel(){return new CancelledState();}
}
