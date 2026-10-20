package sugarrush.application.restaurant;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import sugarrush.domain.restaurant.*;
import sugarrush.domain.user.*;

public final class RestaurantService {
    public record View(String id, String ownerId, String name, double x, double y, Restaurant.Status status,
            List<MenuItem> menu, int queueLength) {
    }

    private final Map<String, Restaurant> restaurants = new LinkedHashMap<>();
    private Path store;

    public void useStore(Path path) throws IOException {
        store = path;
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { p.load(reader); }
        for (String id : p.getProperty("restaurants", "").split(",")) {
            if (id.isBlank() || restaurants.containsKey(id)) continue;
            String prefix = "restaurant." + id + ".";
            Restaurant restaurant = new Restaurant(id, p.getProperty(prefix + "owner"), p.getProperty(prefix + "name"),
                    Double.parseDouble(p.getProperty(prefix + "x")), Double.parseDouble(p.getProperty(prefix + "y")),
                    Restaurant.Status.valueOf(p.getProperty(prefix + "status")));
            for (String itemId : p.getProperty(prefix + "items", "").split(",")) {
                if (itemId.isBlank()) continue;
                String item = prefix + "item." + itemId + ".";
                restaurant.putItem(new MenuItem(itemId, p.getProperty(item + "name"),
                        new BigDecimal(p.getProperty(item + "price")), MenuItem.Status.valueOf(p.getProperty(item + "status")),
                        p.getProperty(item + "description", ""), p.getProperty(item + "imageUrl", "")));
            }
            add(restaurant);
        }
    }

    private void save() {
        if (store == null) return;
        Properties p = new Properties();
        p.setProperty("restaurants", String.join(",", restaurants.keySet()));
        for (Restaurant restaurant : restaurants.values()) {
            String prefix = "restaurant." + restaurant.id() + ".";
            p.setProperty(prefix + "owner", restaurant.ownerId());
            p.setProperty(prefix + "name", restaurant.name());
            p.setProperty(prefix + "x", Double.toString(restaurant.x()));
            p.setProperty(prefix + "y", Double.toString(restaurant.y()));
            p.setProperty(prefix + "status", restaurant.status().name());
            p.setProperty(prefix + "items", String.join(",", restaurant.menu().stream().map(MenuItem::id).toList()));
            for (MenuItem menuItem : restaurant.menu()) {
                String item = prefix + "item." + menuItem.id() + ".";
                p.setProperty(item + "name", menuItem.name());
                p.setProperty(item + "price", menuItem.price().toPlainString());
                p.setProperty(item + "status", menuItem.status().name());
                p.setProperty(item + "description", menuItem.description());
                p.setProperty(item + "imageUrl", menuItem.imageUrl());
            }
        }
        try {
            Files.createDirectories(store.getParent());
            Path temporary = Files.createTempFile(store.getParent(), "restaurants-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) { p.store(writer, "SugarRush restaurants"); }
            Files.move(temporary, store, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save restaurants", failure);
        }
    }

    public View create(User owner, String name) {
        if (owner.role() != Role.RESTAURANT) throw new SecurityException("Restaurant account required");
        if (name == null || name.isBlank() || name.trim().length() > 80) throw new IllegalArgumentException("Enter a restaurant name (up to 80 characters)");
        String id = UUID.randomUUID().toString();
        int hash = Math.abs(id.hashCode() % 10000);
        Restaurant restaurant = new Restaurant(id, owner.id(), name.trim(), 8 + hash % 80, 8 + hash / 100 % 80, Restaurant.Status.OPEN);
        add(restaurant);
        save();
        return browse(owner).stream().filter(view -> view.id().equals(id)).findFirst().orElseThrow();
    }

    public View provision(User owner, String name) {
        if (owner.role() != Role.RESTAURANT) throw new SecurityException("Restaurant account required");
        if (name == null || name.isBlank()) name = "My restaurant";
        if (name.trim().length() > 80)
            throw new IllegalArgumentException("Restaurant name must be at most 80 characters");
        String id = UUID.nameUUIDFromBytes(("signup:" + owner.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        if (!restaurants.containsKey(id)) {
            int hash = Math.abs(id.hashCode() % 10000);
            add(new Restaurant(id, owner.id(), name.trim(), 8 + hash % 80, 8 + hash / 100 % 80, Restaurant.Status.OPEN));
        }
        if (!require(id).ownerId().equals(owner.id())) throw new SecurityException("Restaurant ownership mismatch");
        save();
        return browse(owner).stream().filter(view -> view.id().equals(id)).findFirst().orElseThrow();
    }

    public void changeStatus(String id, Restaurant.Status status) {
        require(id).changeStatus(status);
        save();
    }

    public void putItem(String restaurantId, MenuItem item) {
        require(restaurantId).putItem(item);
        save();
    }

    public void deleteItem(String restaurantId, String itemId) {
        require(restaurantId).removeItem(itemId);
        save();
    }

    public void add(Restaurant restaurant) {
        if (restaurants.putIfAbsent(restaurant.id(), restaurant) != null)
            throw new IllegalArgumentException("Duplicate restaurant");
    }

    public void importIfAbsent(Restaurant restaurant) {
        // Demo fixtures can also exist in the remote store; keep the current seed owner.
        restaurants.putIfAbsent(restaurant.id(), restaurant);
    }

    public Restaurant require(String id) {
        Restaurant restaurant = restaurants.get(id);
        if (restaurant == null)
            throw new IllegalArgumentException("Unknown restaurant: " + id);
        return restaurant;
    }

    public List<View> browse(User user) {
        return restaurants.values().stream()
                .filter(r -> user.role() != Role.RESTAURANT || r.ownerId().equals(user.id())).map(r -> new View(r.id(),
                        r.ownerId(), r.name(), r.x(), r.y(), r.status(), r.menu(), r.queue().snapshot().size()))
                .toList();
    }
}
