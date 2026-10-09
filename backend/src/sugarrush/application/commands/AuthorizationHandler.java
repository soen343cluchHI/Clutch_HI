package sugarrush.application.commands;
import sugarrush.application.order.OrderService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.domain.user.User;
public final class AuthorizationHandler extends CommandHandler {
 private final RestaurantService restaurants;
 private final OrderService orders;
 public AuthorizationHandler(RestaurantService restaurants,OrderService orders,CommandHandler next){super(next);this.restaurants=restaurants;this.orders=orders;}
 public Object handle(Command c,User user){
  if(!c.action().permits(user.role())) throw new SecurityException("Action not permitted for "+user.role());
  String id=switch(c.action()){
   case ACCEPT_ORDER,REJECT_ORDER,START_PREPARING,MARK_READY->orders.require(c.orderId()).restaurantId();
   case RESTAURANT_QUEUE,SET_RESTAURANT_STATUS,PUT_MENU_ITEM->c.restaurantId();
   default->null;
  };
  switch(c.action()){
   case ACCEPT_ORDER,REJECT_ORDER,START_PREPARING,MARK_READY,RESTAURANT_QUEUE,SET_RESTAURANT_STATUS,PUT_MENU_ITEM->{
    if(!restaurants.require(id).ownerId().equals(user.id())) throw new SecurityException("Restaurant ownership required");
   }
   default->{}
  }
  return forward(c,user);
 }
}
