package sugarrush.application.commands;
import sugarrush.application.auth.AuthenticationService;
import sugarrush.application.events.*;
import sugarrush.application.order.OrderService;
import sugarrush.application.restaurant.RestaurantService;
public final class CommandProcessor {
 private final CommandHandler first;
 private final CommandLog log;
 public CommandProcessor(AuthenticationService auth,RestaurantService restaurants,OrderService orders,EventLogger events,DashboardObserver dashboard,CommandLog log){
  this.log=log;first=new AuthenticationHandler(auth,new AuthorizationHandler(restaurants,orders,new ExecutionHandler(auth,restaurants,orders,events,dashboard,log)));
 }
 // Serialize in-memory commands so state, queue and event updates cannot interleave.
 public synchronized Object process(Command command){
  try{
   Object result=first.handle(command,null);log.record(command.action(),true,"Executed");return result;
  }catch(RuntimeException failure){log.record(command==null?null:command.action(),false,failure.getMessage());throw failure;}
 }
}
