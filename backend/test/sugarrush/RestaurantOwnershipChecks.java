package sugarrush;

import java.math.BigDecimal;
import java.nio.file.*;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.domain.restaurant.MenuItem;
import sugarrush.domain.user.Role;
import sugarrush.domain.user.User;

public final class RestaurantOwnershipChecks {
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("restaurant-checks");
        Path store = folder.resolve("restaurants.properties");
        try {
            User owner = new User("firebase:owner", "owner@example.com", Role.RESTAURANT);
            User other = new User("firebase:other", "other@example.com", Role.RESTAURANT);
            RestaurantService first = new RestaurantService();
            first.useStore(store);
            var created = first.create(owner, "My Restaurant");
            first.putItem(created.id(), new MenuItem("coffee", "Coffee", new BigDecimal("3.50"), MenuItem.Status.AVAILABLE));
            first.changeStatus(created.id(), sugarrush.domain.restaurant.Restaurant.Status.PAUSED);
            RestaurantService restored = new RestaurantService();
            restored.useStore(store);
            if (restored.browse(owner).size() != 1 || !restored.browse(other).isEmpty()
                    || restored.require(created.id()).menu().size() != 1
                    || restored.require(created.id()).status() != sugarrush.domain.restaurant.Restaurant.Status.PAUSED)
                throw new AssertionError("Restaurant ownership or persistence failed");
            restored.deleteItem(created.id(), "coffee");
            RestaurantService afterDelete = new RestaurantService();
            afterDelete.useStore(store);
            if (!afterDelete.require(created.id()).menu().isEmpty())
                throw new AssertionError("Menu deletion was not saved");
            System.out.println("Restaurant ownership and persistence checks passed.");
        } finally {
            Files.deleteIfExists(store);
            Files.deleteIfExists(folder);
        }
    }
}
