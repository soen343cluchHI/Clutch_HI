package sugarrush.domain.order;
public final class PreparingState implements OrderState {
 public String name(){return "Preparing";}
 public OrderState ready(){return new ReadyState();}
}
