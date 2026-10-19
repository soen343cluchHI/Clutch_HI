package sugarrush.application.profile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import sugarrush.domain.user.*;

public final class ProfileService {
    public record View(String email, Role role, Profile profile, List<String> editableFields) {
    }

    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private Path store;

    public void useStore(Path path) throws IOException {
        store = path;
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { p.load(reader); }
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith("profile.") || !key.endsWith(".displayName")) continue;
            String id = key.substring("profile.".length(), key.length() - ".displayName".length());
            String prefix = "profile." + id + ".";
            put(id, new Profile(p.getProperty(prefix + "displayName"), p.getProperty(prefix + "phone"),
                    p.getProperty(prefix + "deliveryAddress"), p.getProperty(prefix + "vehicle")));
        }
    }

    private void save() {
        if (store == null) return;
        Properties p = new Properties();
        profiles.forEach((id, profile) -> profile.values().forEach((field, value) -> p.setProperty("profile." + id + "." + field, value)));
        try {
            Files.createDirectories(store.getParent());
            Path temporary = Files.createTempFile(store.getParent(), "profiles-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) { p.store(writer, "SugarRush profiles"); }
            Files.move(temporary, store, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save profiles", failure);
        }
    }

    public void put(String userId, Profile profile) {
        profiles.put(Objects.requireNonNull(userId), Objects.requireNonNull(profile));
    }

    public View view(User user) {
        return new View(user.username(), user.role(), profiles.getOrDefault(user.id(), Profile.EMPTY),
                Profile.editableFields(user.role()));
    }

    public View update(User user, Map<String, String> changes) {
        if (changes == null || changes.isEmpty())
            throw new IllegalArgumentException("No profile changes provided");
        put(user.id(), profiles.getOrDefault(user.id(), Profile.EMPTY).update(user.role(), changes));
        save();
        return view(user);
    }
}
