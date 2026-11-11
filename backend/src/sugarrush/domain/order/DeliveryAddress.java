package sugarrush.domain.order;

// Where the courier drops the order: a readable address plus its map coordinates in kilometres.
public record DeliveryAddress(String label, double x, double y) {
    public DeliveryAddress {
        if (label == null || label.isBlank())
            throw new IllegalArgumentException("Delivery address required");
        if (label.trim().length() > 200)
            throw new IllegalArgumentException("Delivery address must be at most 200 characters");
        if (!Double.isFinite(x) || !Double.isFinite(y))
            throw new IllegalArgumentException("Delivery address needs valid map coordinates");
        label = label.trim();
    }
}
