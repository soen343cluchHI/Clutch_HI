package sugarrush.domain.order;
public interface OrderState {
 String name();
 default OrderState cancel(){throw invalid("cancel");}
 default OrderState accept(){throw invalid("accept");}
 default OrderState reject(){throw invalid("reject");}
 default OrderState prepare(){throw invalid("prepare");}
 default OrderState ready(){throw invalid("ready");}
 default OrderState pickUp(){throw invalid("pick up");}
 default OrderState deliver(){throw invalid("deliver");}
 default IllegalStateException invalid(String action){return new IllegalStateException("Cannot "+action+" in "+name());}
}
