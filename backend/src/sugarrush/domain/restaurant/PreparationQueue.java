package sugarrush.domain.restaurant;

import java.util.*;

public final class PreparationQueue {
    private final Deque<String> orders = new ArrayDeque<>();

    public void add(String id) {
        if (orders.contains(id))
            throw new IllegalStateException("Already queued");
        orders.addLast(Objects.requireNonNull(id));
    }

    public void requireHead(String id) {
        if (!Objects.equals(orders.peekFirst(), id))
            throw new IllegalStateException("Prepare in arrival order");
    }

    public void complete(String id) {
        requireHead(id);
        orders.removeFirst();
    }

    public void remove(String id) {
        orders.remove(id);
    }

    public List<String> snapshot() {
        return List.copyOf(orders);
    }
}
