package sugarrush.domain.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
        // a Java record keeps these fields immutable
public record LedgerEntry(//This defines one ledger row
        UUID entryId,
        UUID eventId,
        String orderId,
        //Customer and restaurant IDs will help us filter history
        String customerId,
        String restaurantId,
        Type type,
        String state,
        Instant placedAt,
        Instant recordedAt,
        BigDecimal totalCharged) {

    public enum Type {
        ORDER_STATE,
        CHARGE,
        NO_CHARGE,
        REFUND
    }
}