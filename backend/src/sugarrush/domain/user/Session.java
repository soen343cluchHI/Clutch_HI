package sugarrush.domain.user;

import java.time.Instant;

public record Session(String token, User user, Instant createdAt) {
}
