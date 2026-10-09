package sugarrush.application;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import sugarrush.application.auth.*;
import sugarrush.application.commands.*;
import sugarrush.application.events.*;
import sugarrush.application.order.OrderService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.application.seed.SeedLoader;
import sugarrush.domain.user.*;
public final class SugarRush {
 private final RegistrationService registration=new RegistrationService();
 private final AuthenticationService authentication=new AuthenticationService(registration);
 private final RestaurantService restaurants=new RestaurantService();
 private final EventLogger events=new EventLogger();
 private final DashboardObserver dashboard=new DashboardObserver();
 private final OrderEventPublisher publisher=new OrderEventPublisher();
 private final CommandLog log=new CommandLog();
 private final OrderService orders=new OrderService(restaurants,publisher);
 private final CommandProcessor commands=new CommandProcessor(authentication,restaurants,orders,events,dashboard,log);
 private SugarRush(){publisher.subscribe(events);publisher.subscribe(dashboard);}
 public static SugarRush fromSeed(Path path)throws IOException{
  SugarRush app=new SugarRush();new SeedLoader().load(path,app.registration,app.restaurants);return app;
 }
 // Login and registration bootstrap access; session-bound actions all use execute.
 public synchronized User register(String username,String password,Collection<Role> roles){
  if(roles!=null && roles.contains(Role.OPERATOR)) throw new SecurityException("Operator accounts require trusted provisioning");
  return registration.register(username,password,roles);
 }
 public synchronized Session login(String username,String password){return authentication.login(username,password);}
 public synchronized Object execute(Command command){return commands.process(command);}
}
