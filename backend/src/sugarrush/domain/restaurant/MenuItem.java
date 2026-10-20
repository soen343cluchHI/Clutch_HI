package sugarrush.domain.restaurant;

import java.math.BigDecimal;
import java.util.Objects;

public record MenuItem(String id, String name, BigDecimal price, Status status, String description, String imageUrl) {
    public MenuItem(String id, String name, BigDecimal price, Status status) {
        this(id, name, price, status, "", "");
    }
    public enum Status {
        AVAILABLE, SOLD_OUT
    }

    public MenuItem {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}") || name == null || name.isBlank() || name.length() > 80)
            throw new IllegalArgumentException("Item identity required");
        if (price == null || price.signum() < 0)
            throw new IllegalArgumentException("Nonnegative price required");
        Objects.requireNonNull(status);
        description = description == null ? "" : description.trim();
        imageUrl = imageUrl == null ? "" : imageUrl.trim();
        if (description.length() > 500) throw new IllegalArgumentException("Description must be at most 500 characters");
        if (!imageUrl.isEmpty() && (imageUrl.length() > 2048 || !imageUrl.matches("https?://[^\\s]+")))
            throw new IllegalArgumentException("Enter a valid http or https image URL");
    }
}
