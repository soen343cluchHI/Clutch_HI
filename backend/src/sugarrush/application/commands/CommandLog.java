package sugarrush.application.commands;

import java.time.Instant;
import java.util.*;

public final class CommandLog {
    
    public record Entry(UUID id, Action action, boolean success, String message, Instant timestamp) {}

    private final List<Entry> entries = new ArrayList<>();

    public void record(Action action, boolean success, String message) {
        entries.add(new Entry(UUID.randomUUID(), action, success, message, Instant.now()));
    }

    public List<Entry> all() {
        return List.copyOf(entries);
    }
}
