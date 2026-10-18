package sugarrush.application.auth;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import sugarrush.domain.user.*;

public final class RegistrationService {
   
    private record Credential(User user, byte[] salt, byte[] hash) {}

    private final Map<String, Credential> accounts = new LinkedHashMap<>();

    public User register(String username, String password, Collection<Role> roles) {
        if (roles == null || roles.size() != 1 || roles.iterator().next() == null)
            throw new IllegalArgumentException("Select exactly one role");

        if (username == null || username.isBlank() || password == null || password.length() < 6)
            throw new IllegalArgumentException("Username and password of at least six characters required");

        String key = username.trim().toLowerCase(Locale.ROOT);

        if (accounts.containsKey(key))
            throw new IllegalArgumentException("Username already registered");

        User user = new User(UUID.randomUUID().toString(), username.trim(), roles.iterator().next());
        byte[] salt = new byte[16];

        new SecureRandom().nextBytes(salt);
        accounts.put(key, new Credential(user, salt, hash(password, salt)));

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
