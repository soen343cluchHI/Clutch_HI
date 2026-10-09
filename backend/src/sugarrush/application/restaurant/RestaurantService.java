package sugarrush.application.restaurant;
import java.util.*;
import sugarrush.domain.restaurant.*;
public final class RestaurantService {
 public record View(String id,String name,double x,double y,Restaurant.Status status,List<MenuItem> menu){}
 private final Map<String,Restaurant> restaurants=new LinkedHashMap<>();
 public void add(Restaurant restaurant){
  if(restaurants.putIfAbsent(restaurant.id(),restaurant)!=null) throw new IllegalArgumentException("Duplicate restaurant");
 }
 public Restaurant require(String id){
  Restaurant restaurant=restaurants.get(id);
  if(restaurant==null) throw new IllegalArgumentException("Unknown restaurant: "+id);
  return restaurant;
 }
 public List<View> browse(){return restaurants.values().stream().map(r->new View(r.id(),r.name(),r.x(),r.y(),r.status(),r.menu())).toList();}
}
