package sugarrush;

import java.time.Instant;
import java.util.UUID;

public record OrderEvent(UUID eventId, String orderId, String previousState,
                         String newState, Instant timestamp) {}
