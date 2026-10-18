package sugarrush;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import sugarrush.application.SugarRush;
import sugarrush.application.commands.*;
import sugarrush.application.events.*;
import sugarrush.application.order.OrderService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.domain.order.*;
import sugarrush.domain.restaurant.*;
import sugarrush.domain.user.*;

public final class ArchitectureChecks {
 private static int checks;
 public static void main(String[] args) throws Exception {
  SugarRush app=SugarRush.fromSeed(Path.of("backend/config/demo.properties"));
  Session customer=app.login("customer","customer-demo");
  Session staff=app.login("restaurant","restaurant-demo");
  Session other=app.login("restaurant2","restaurant-demo");
  Session courier=app.login("courier","courier-demo");
  Session operator=app.login("operator","operator-demo");
  expect(SecurityException.class,()->app.login("customer","incorrect"));
  expect(IllegalArgumentException.class,()->app.register("none","password",List.of()));
  expect(IllegalArgumentException.class,()->app.register("two","password",List.of(Role.CUSTOMER,Role.COURIER)));
  expect(SecurityException.class,()->app.register("admin","password",List.of(Role.OPERATOR)));
  expect(IllegalArgumentException.class,()->app.register("CUSTOMER","password",List.of(Role.CUSTOMER)));
  expect(SecurityException.class,()->app.execute(new Command("",Action.BROWSE_RESTAURANTS)));
  expect(SecurityException.class,()->app.execute(new Command("fake",Action.ROLE_MENU)));
  expect(SecurityException.class,()->app.execute(new Command(customer.token(),Action.EVENT_LOG)));
  for(Session session:List.of(customer,staff,courier,operator)){
   Object menu=app.execute(new Command(session.token(),Action.ROLE_MENU));
   check(menu.equals(Action.menu(session.user().role())),"Incorrect role menu");
  }
  check(!Action.menu(Role.COURIER).contains(Action.PLACE_ORDER),"Courier must not place orders");
  check(!Action.menu(Role.OPERATOR).contains(Action.ACCEPT_ORDER),"Operator must not accept orders");
  // Arrange the preconditions here instead of relying on the demo seed's starting state.
  app.execute(new Command(staff.token(),Action.PUT_MENU_ITEM,"sugar-shop",null,null,null,null,new MenuItem("cookie","Cookie",new BigDecimal("3.00"),MenuItem.Status.SOLD_OUT)));
  app.execute(new Command(other.token(),Action.SET_RESTAURANT_STATUS,"other-shop",null,null,null,Restaurant.Status.PAUSED,null));
  List<?> restaurants=(List<?>)app.execute(new Command(customer.token(),Action.BROWSE_RESTAURANTS));
  check(restaurants.size()==2,"Paused restaurant must remain visible");
  RestaurantService.View shop=(RestaurantService.View)restaurants.get(0);
  check(shop.menu().stream().anyMatch(i->i.id().equals("cookie")),"Sold-out items must remain visible");
  expect(UnsupportedOperationException.class,()->shop.menu().clear());
  expect(IllegalArgumentException.class,()->app.execute(Command.place(customer.token(),"sugar-shop",Map.of("cookie",1))));
  expect(IllegalArgumentException.class,()->app.execute(Command.place(customer.token(),"sugar-shop",Map.of("cake",0))));
  expect(IllegalArgumentException.class,()->app.execute(Command.place(customer.token(),"sugar-shop",Map.of("cake",-1))));
  expect(IllegalArgumentException.class,()->app.execute(Command.place(customer.token(),"sugar-shop",Map.of())));
  expect(IllegalArgumentException.class,()->app.execute(Command.place(customer.token(),"sugar-shop",Map.of("icecream",1))));
  expect(IllegalStateException.class,()->app.execute(Command.place(customer.token(),"other-shop",Map.of("vanilla",1))));
  expect(SecurityException.class,()->app.execute(Command.place(courier.token(),"sugar-shop",Map.of("cake",1))));
  OrderService.View first=place(app,customer.token());
  OrderService.View second=place(app,customer.token());
  check(first.total().equals(new BigDecimal("17.00")),"Incorrect price snapshot");
  expect(SecurityException.class,()->app.execute(Command.order(other.token(),Action.ACCEPT_ORDER,first.id(),null)));
  expect(SecurityException.class,()->app.execute(Command.order(customer.token(),Action.ACCEPT_ORDER,first.id(),null)));
  int before=events(app,operator.token()).size();
  expect(IllegalStateException.class,()->app.execute(Command.order(staff.token(),Action.MARK_READY,first.id(),null)));
  check(events(app,operator.token()).size()==before,"Invalid transition published event");
  transition(app,staff.token(),Action.ACCEPT_ORDER,first.id());
  transition(app,staff.token(),Action.ACCEPT_ORDER,second.id());
  check(queue(app,staff.token()).equals(List.of(first.id(),second.id())),"Queue order incorrect");
  expect(IllegalStateException.class,()->transition(app,staff.token(),Action.START_PREPARING,second.id()));
  transition(app,staff.token(),Action.START_PREPARING,first.id());
  transition(app,staff.token(),Action.MARK_READY,first.id());
  check(queue(app,staff.token()).equals(List.of(second.id())),"Ready order remains queued");
  transition(app,staff.token(),Action.START_PREPARING,second.id());
  transition(app,staff.token(),Action.MARK_READY,second.id());
  expect(IllegalStateException.class,()->transition(app,staff.token(),Action.ACCEPT_ORDER,first.id()));
  OrderService.View rejected=place(app,customer.token());
  expect(IllegalArgumentException.class,()->app.execute(Command.order(staff.token(),Action.REJECT_ORDER,rejected.id()," ")));
  OrderService.View rejectedView=(OrderService.View)app.execute(Command.order(staff.token(),Action.REJECT_ORDER,rejected.id(),"Kitchen unavailable"));
  check("Kitchen unavailable".equals(rejectedView.rejectionReason()),"Missing rejection reason");
  expect(IllegalStateException.class,()->transition(app,staff.token(),Action.ACCEPT_ORDER,rejected.id()));
  expect(SecurityException.class,()->app.execute(new Command(other.token(),Action.RESTAURANT_QUEUE,"sugar-shop",null,null,null,null,null)));
  expect(SecurityException.class,()->app.execute(new Command(other.token(),Action.PUT_MENU_ITEM,"sugar-shop",null,null,null,null,new MenuItem("cake","Cake",BigDecimal.ONE,MenuItem.Status.AVAILABLE))));
  app.execute(new Command(staff.token(),Action.PUT_MENU_ITEM,"sugar-shop",null,null,null,null,new MenuItem("cake","Cake",BigDecimal.ONE,MenuItem.Status.AVAILABLE)));
  check(first.total().equals(new BigDecimal("17.00")),"Menu editing changed historical price");
  expect(SecurityException.class,()->app.execute(new Command(other.token(),Action.SET_RESTAURANT_STATUS,"sugar-shop",null,null,null,Restaurant.Status.PAUSED,null)));
  expect(SecurityException.class,()->app.execute(new Command(customer.token(),Action.SET_RESTAURANT_STATUS,"sugar-shop",null,null,null,Restaurant.Status.PAUSED,null)));
  expect(SecurityException.class,()->app.execute(new Command(courier.token(),Action.SET_RESTAURANT_STATUS,"sugar-shop",null,null,null,Restaurant.Status.PAUSED,null)));
  app.execute(new Command(operator.token(),Action.SET_RESTAURANT_STATUS,"other-shop",null,null,null,Restaurant.Status.PAUSED,null));
  app.execute(new Command(staff.token(),Action.SET_RESTAURANT_STATUS,"sugar-shop",null,null,null,Restaurant.Status.CLOSED,null));
  expect(IllegalStateException.class,()->place(app,customer.token()));
  User another=app.register("another","password",List.of(Role.CUSTOMER));
  Session anotherSession=app.login(another.username(),"password");
  check(((List<?>)app.execute(new Command(anotherSession.token(),Action.LIST_ORDERS))).isEmpty(),"Customer can see another user's orders");
  check(((List<?>)app.execute(new Command(other.token(),Action.LIST_ORDERS))).isEmpty(),"Staff can see another restaurant's orders");
  check(((List<?>)app.execute(new Command(customer.token(),Action.LIST_ORDERS))).size()==3,"Missing customer orders");
  Map<?,?> dashboard=(Map<?,?>)app.execute(new Command(operator.token(),Action.DASHBOARD));
  check("Ready".equals(dashboard.get(first.id())),"Dashboard not updated");
  check("Rejected".equals(dashboard.get(rejected.id())),"Rejection not observed");
  Set<UUID> ids=new HashSet<>();
  List<OrderEvent> events=events(app,operator.token());
  for(OrderEvent event:events){check(ids.add(event.eventId()),"Duplicate event ID");check(event.timestamp()!=null,"Missing timestamp");}
  check(events.size()==10,"Incorrect event count");
  check(events.get(0).previousState()==null && events.get(0).newState().equals("Placed"),"Creation event missing");
  List<?> commands=(List<?>)app.execute(new Command(operator.token(),Action.COMMAND_LOG));
  check(commands.stream().map(CommandLog.Entry.class::cast).anyMatch(e->!e.success()),"Errors not recorded");
  app.execute(new Command(customer.token(),Action.LOGOUT));
  expect(SecurityException.class,()->app.execute(new Command(customer.token(),Action.ROLE_MENU)));
  check(app.execute(new Command(anotherSession.token(),Action.SESSION_INFO)).equals(another),"Logout invalidated another session");

  // Domain invariants hold even without application services.
  Restaurant r=new Restaurant("r","owner","Shop",0,0,Restaurant.Status.OPEN);
  MenuItem item=new MenuItem("i","Cake",BigDecimal.ONE,MenuItem.Status.AVAILABLE);
  r.putItem(item);
  expect(IllegalArgumentException.class,()->new OrderLine(item,0));
  Order order=new Order("o","c",r,List.of(new OrderLine(item,1)));
  expect(IllegalStateException.class,order::deliver);
  order.accept();order.prepare();order.ready();order.pickUp();order.deliver();
  expect(IllegalStateException.class,order::pickUp);
  check(order.state().equals("Delivered"),"Delivery state pattern incomplete");
  expect(IllegalStateException.class,order::cancel);
  Order cancelled=new Order("o2","c",r,List.of(new OrderLine(item,1)));
  cancelled.accept();cancelled.prepare();cancelled.cancel();
  expect(IllegalStateException.class,cancelled::cancel);
  PreparationQueue queue=new PreparationQueue();
  queue.add("a");queue.add("b");queue.add("c");queue.remove("b");
  check(queue.snapshot().equals(List.of("a","c")),"Cancellation broke arrival order");
  Restaurant wrong=new Restaurant("wrong","owner","Other",0,0,Restaurant.Status.OPEN);
  expect(IllegalArgumentException.class,()->new Order("bad","c",wrong,List.of(new OrderLine(item,1))));
  OrderEventPublisher publisher=new OrderEventPublisher();
  EventLogger logger=new EventLogger();
  publisher.subscribe(e->{throw new IllegalStateException("Expected test observer failure");});
  publisher.subscribe(logger);
  publisher.publish(order.placedEvent());
  check(logger.all().size()==1,"Failed observer prevented later delivery");
  System.out.println("Passed "+checks+" architecture and business-rule checks.");
 }
 private static OrderService.View place(SugarRush app,String token){return (OrderService.View)app.execute(Command.place(token,"sugar-shop",Map.of("cake",2)));}
 private static void transition(SugarRush app,String token,Action action,String id){app.execute(Command.order(token,action,id,null));}
 private static List<?> queue(SugarRush app,String token){return (List<?>)app.execute(new Command(token,Action.RESTAURANT_QUEUE,"sugar-shop",null,null,null,null,null));}
 @SuppressWarnings("unchecked")
 private static List<OrderEvent> events(SugarRush app,String token){return (List<OrderEvent>)app.execute(new Command(token,Action.EVENT_LOG));}
 private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 private static void expect(Class<? extends RuntimeException> type,Runnable action){
  checks++;
  try{action.run();}catch(RuntimeException failure){if(type.isInstance(failure))return;throw failure;}
  throw new AssertionError("Expected "+type.getSimpleName());
 }
}
