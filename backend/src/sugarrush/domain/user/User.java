package sugarrush.domain.user;

import java.util.Objects;

public record User(String id, String username, Role role) {
    public User {
        if (id == null || id.isBlank() || username == null || username.isBlank())
            throw new IllegalArgumentException("User identity required");
        Objects.requireNonNull(role, "Exactly one role required");
    }
}
