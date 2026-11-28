package sugarrush.application.auth;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import sugarrush.domain.user.*;

public final class RegistrationService {
   
    private record Credential(User user, byte[] salt, byte[] hash) {}

    private final Map<String, Credential> accounts = new LinkedHashMap<>();
    private Path store;

    public void useStore(Path path) throws IOException {
        store = path;
        if (!Files.exists(path)) return;
        Properties saved = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { saved.load(reader); }
        int count = Integer.parseInt(saved.getProperty("accounts", "0"));
        for (int i = 0; i < count; i++) {
            String prefix = "account." + i + ".";
            String username = saved.getProperty(prefix + "username");
            User user = new User(saved.getProperty(prefix + "id"), username,
                    Role.valueOf(saved.getProperty(prefix + "role")));
            byte[] salt = Base64.getDecoder().decode(saved.getProperty(prefix + "salt"));
            byte[] hash = Base64.getDecoder().decode(saved.getProperty(prefix + "hash"));
            accounts.putIfAbsent(username.trim().toLowerCase(Locale.ROOT), new Credential(user, salt, hash));
        }
    }

    private void save() {
        if (store == null) return;
        Properties saved = new Properties();
        saved.setProperty("accounts", Integer.toString(accounts.size()));
        int index = 0;
        for (Credential credential : accounts.values()) {
            String prefix = "account." + index++ + ".";
            saved.setProperty(prefix + "id", credential.user().id());
            saved.setProperty(prefix + "username", credential.user().username());
            saved.setProperty(prefix + "role", credential.user().role().name());
            saved.setProperty(prefix + "salt", Base64.getEncoder().encodeToString(credential.salt()));
            saved.setProperty(prefix + "hash", Base64.getEncoder().encodeToString(credential.hash()));
        }
        try {
            Path parent = store.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, "accounts-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) { saved.store(writer, "SugarRush demo accounts; password hashes only"); }
            Files.move(temporary, store, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save demo accounts", failure);
        }
    }

    public User register(String username, String password, Collection<Role> roles) {
        return registerWithId(UUID.randomUUID().toString(), username, password, roles);
    }

    public User registerWithId(String id, String username, String password, Collection<Role> roles) {
        if (roles == null || roles.size() != 1 || roles.iterator().next() == null)
            throw new IllegalArgumentException("Select exactly one role");

        if (username == null || username.isBlank() || password == null || password.length() < 6)
            throw new IllegalArgumentException("Username and password of at least six characters required");

        String key = username.trim().toLowerCase(Locale.ROOT);

        if (accounts.containsKey(key))
            throw new IllegalArgumentException("Username already registered");

        User user = new User(id, username.trim(), roles.iterator().next());
        byte[] salt = new byte[16];

        new SecureRandom().nextBytes(salt);
        accounts.put(key, new Credential(user, salt, hash(password, salt)));
        try { save(); }
        catch (RuntimeException failure) { accounts.remove(key); throw failure; }

        return user;
    }

    User authenticate(String username, String password) {
        Credential c = username == null ? null : accounts.get(username.trim().toLowerCase(Locale.ROOT));
        if (c == null || password == null || !MessageDigest.isEqual(c.hash(), hash(password, c.salt())))
            throw new SecurityException("Invalid credentials");
        return c.user();
    }

    public User byUsername(String username) {
        Credential c = accounts.get(username.trim().toLowerCase(Locale.ROOT));
        if (c == null)
            throw new IllegalArgumentException("Unknown user: " + username);
        return c.user();
    }

    private static byte[] hash(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 120000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        } finally {
            spec.clearPassword();
        }
    }
}
