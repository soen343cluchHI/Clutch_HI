package sugarrush.application.auth;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import sugarrush.domain.user.Role;

/** Self-service enrollment, separate from editable Firebase profile metadata. */
public final class FirebaseRegistrationService {
    public record Enrollment(Role role, String restaurantName) {
        public Enrollment {
            if (role == null)
                throw new IllegalArgumentException("Choose an account type");
            restaurantName = restaurantName == null ? "" : restaurantName.trim();
            if (role == Role.RESTAURANT && restaurantName.length() > 80)
                throw new IllegalArgumentException("Restaurant name must be at most 80 characters");
            if (role != Role.RESTAURANT && !restaurantName.isEmpty())
                throw new IllegalArgumentException("Only restaurant accounts can specify a restaurant name");
        }
    }

    private final Map<String, Enrollment> accounts = new HashMap<>();
    private Path store;

    public void useStore(Path path) throws IOException {
        store = path;
        if (!Files.exists(path)) return;
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { properties.load(reader); }
        for (String key : properties.stringPropertyNames()) {
            if (!key.endsWith(".role")) continue;
            String encoded = key.substring(0, key.length() - 5);
            String uid = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            accounts.put(uid, new Enrollment(Role.valueOf(properties.getProperty(key)),
                    properties.getProperty(encoded + ".restaurantName", "")));
        }
    }

    public Enrollment find(String uid) { return accounts.get(uid); }

    public void register(String uid, Enrollment enrollment) {
        if (uid == null || uid.isBlank()) throw new SecurityException("Verified Firebase UID required");
        Enrollment existing = accounts.get(uid);
        if (existing != null) {
            if (!existing.equals(enrollment)) throw new IllegalStateException("This account is already registered with different details");
            return;
        }
        Map<String, Enrollment> next = new HashMap<>(accounts);
        next.put(uid, enrollment);
        if (store != null) {
            Properties properties = new Properties();
            next.forEach((id, value) -> {
                String prefix = Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
                properties.setProperty(prefix + ".role", value.role().name());
                properties.setProperty(prefix + ".restaurantName", value.restaurantName());
            });
            try {
                Files.createDirectories(store.toAbsolutePath().getParent());
                Path temporary = Files.createTempFile(store.toAbsolutePath().getParent(), "accounts-", ".tmp");
                try (Writer writer = Files.newBufferedWriter(temporary)) { properties.store(writer, "Firebase UID enrollments; no passwords"); }
                Files.move(temporary, store, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) { throw new IllegalStateException("Could not persist account registration", failure); }
        }
        accounts.put(uid, enrollment);
    }
}
