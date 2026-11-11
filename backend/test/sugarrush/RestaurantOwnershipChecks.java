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
            // Every restaurant has a real Montreal street address, and a move is saved.
            var placed = afterDelete.require(created.id());
            if (!placed.address().endsWith("Montréal") && !placed.address().endsWith("Verdun"))
                throw new AssertionError("New restaurant needs a Montreal street address");
            afterDelete.relocate(created.id(), "2000 Rue Peel, Montréal", -0.61, -0.09);
            RestaurantService moved = new RestaurantService();
            moved.useStore(store);
            var reloaded = moved.require(created.id());
            if (!reloaded.address().equals("2000 Rue Peel, Montréal") || reloaded.x() != -0.61 || reloaded.y() != -0.09)
                throw new AssertionError("Restaurant move was not saved");
            // Stores written before addresses existed hold far-away grid positions; they are moved into Montreal.
            Path legacy = folder.resolve("legacy.properties");
            Files.writeString(legacy, "restaurants=old\nrestaurant.old.owner=firebase:owner\nrestaurant.old.name=Old Shop\n"
                    + "restaurant.old.x=55.0\nrestaurant.old.y=71.0\nrestaurant.old.status=OPEN\nrestaurant.old.items=\n");
            RestaurantService upgraded = new RestaurantService();
            upgraded.useStore(legacy);
            var old = upgraded.require("old");
            if (!old.hasAddress() || Math.abs(old.x()) > 5 || Math.abs(old.y()) > 5)
                throw new AssertionError("Legacy restaurant was not placed in Montreal");
            Files.deleteIfExists(legacy);
            System.out.println("Restaurant ownership and persistence checks passed.");
        } finally {
            Files.deleteIfExists(store);
            Files.deleteIfExists(folder);
        }
    }
}
