package sugarrush.application.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import sugarrush.domain.billing.LedgerEntry;
import sugarrush.domain.order.Order;
import sugarrush.domain.order.OrderEvent;

//this stores ledger entries, classifies them, prevents duplicate processing of the same event, and returns a list callers cannot modify
public final class LedgerService {
                                        // when the order was created
    private final Map<String, Instant> placedAt = new HashMap<>();

    private final Map<UUID, LedgerEntry> entries = new LinkedHashMap<>();

    public void record(
            Order order,
            OrderEvent event,
            BigDecimal totalCharged) {

        if (!order.id().equals(event.orderId())) {
            throw new IllegalArgumentException(
                    "Order and event do not match");
        }

        //Recording the same event again must not duplicate its entry.
        if (entries.containsKey(event.eventId())) {
            return;
        }

        Instant created = placedAt.get(order.id());

        if (created == null) {
            if (!"Placed".equals(event.newState())
                    || event.previousState() != null) {
                throw new IllegalStateException(
                        "Placement must be recorded first");
            }

            created = event.timestamp();
        }

        LedgerEntry.Type type = switch (event.newState()) {
            case "Delivered" -> LedgerEntry.Type.CHARGE;
            case "Refunded" -> LedgerEntry.Type.REFUND;
            case "Cancelled", "Rejected" -> LedgerEntry.Type.NO_CHARGE;
            default -> LedgerEntry.Type.ORDER_STATE;
        };

        Objects.requireNonNull(totalCharged);

        boolean financialEntry = type == LedgerEntry.Type.CHARGE || type == LedgerEntry.Type.REFUND;

        if (totalCharged.signum() < 0 || (!financialEntry && totalCharged.signum() != 0)) {
            throw new IllegalArgumentException("Invalid charge for order state");
}

        if (type == LedgerEntry.Type.REFUND && totalCharged.compareTo(refundableAmount(order.id())) != 0) {
            throw new IllegalArgumentException("Refund must match the original charge");
        }

        LedgerEntry entry = new LedgerEntry(
                UUID.randomUUID(),
                event.eventId(),
                order.id(),
                order.customerId(),
                order.restaurantId(),
                type,
                event.newState(),
                created,
                event.timestamp(),
                totalCharged);

        placedAt.putIfAbsent(order.id(), created);
        entries.put(event.eventId(), entry);
    }

    public BigDecimal refundableAmount(String orderId) {
        boolean refunded = entries.values().stream().anyMatch(entry ->
                        entry.orderId().equals(orderId)
                        && entry.type() == LedgerEntry.Type.REFUND);

        if (refunded) {
            throw new IllegalStateException("Order has already been refunded");
        }

        return entries.values().stream()
                .filter(entry ->
                        entry.orderId().equals(orderId)
                        && entry.type() == LedgerEntry.Type.CHARGE)
                .map(LedgerEntry::totalCharged)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No delivered charge is available to refund"));
    }

    public List<LedgerEntry> all() {
        return List.copyOf(entries.values());
    }
}