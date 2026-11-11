package sugarrush.domain.restaurant;

import java.util.*;

public final class Restaurant {
    public enum Status {
        OPEN, PAUSED, CLOSED
    }

    private final String id, ownerId, name;
    // Street address plus map position in kilometres from the city origin; blank when not set yet.
    private String address = "";
    private double x, y;
    private Status status;
    private final Map<String, MenuItem> menu = new LinkedHashMap<>();
    private final PreparationQueue queue = new PreparationQueue();

    public Restaurant(String id, String ownerId, String name, double x, double y, Status status) {
        if (id == null || id.isBlank() || ownerId == null || ownerId.isBlank() || name == null || name.isBlank())
            throw new IllegalArgumentException("Restaurant identity required");
        if (!Double.isFinite(x) || !Double.isFinite(y))
            throw new IllegalArgumentException("Invalid position");
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.x = x;
        this.y = y;
        this.status = Objects.requireNonNull(status);
    }

    public String id() {
        return id;
    }

    public String ownerId() {
        return ownerId;
    }

    public String name() {
        return name;
    }

    public String address() {
        return address;
    }

    public boolean hasAddress() {
        return !address.isBlank();
    }

    public void relocate(String address, double x, double y) {
        if (address == null || address.isBlank())
            throw new IllegalArgumentException("Restaurant address required");
        if (address.trim().length() > 200)
            throw new IllegalArgumentException("Restaurant address must be at most 200 characters");
        if (!Double.isFinite(x) || !Double.isFinite(y))
            throw new IllegalArgumentException("Invalid position");
        this.address = address.trim();
        this.x = x;
        this.y = y;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public Status status() {
        return status;
    }

    public void changeStatus(Status status) {
        this.status = Objects.requireNonNull(status);
    }

    public void putItem(MenuItem item) {
        menu.put(item.id(), item);
    }

    public void removeItem(String id) {
        if (menu.remove(id) == null) throw new IllegalArgumentException("Unknown menu item: " + id);
    }

    public List<MenuItem> menu() {
        return List.copyOf(menu.values());
    }

    public PreparationQueue queue() {
        return queue;
    }

    public MenuItem item(String id) {
        MenuItem item = menu.get(id);
        if (item == null)
            throw new IllegalArgumentException("Unknown menu item: " + id);
        return item;
    }

    public void requireOpen() {
        if (status != Status.OPEN)
            throw new IllegalStateException("Restaurant is " + status);
    }
}
