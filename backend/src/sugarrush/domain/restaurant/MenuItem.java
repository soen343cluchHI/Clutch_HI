package sugarrush.domain.restaurant;

import java.math.BigDecimal;
import java.util.Objects;

public record MenuItem(String id, String name, BigDecimal price, Status status) {
    public enum Status {
        AVAILABLE, SOLD_OUT
    }

    public MenuItem {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}") || name == null || name.isBlank() || name.length() > 80)
            throw new IllegalArgumentException("Item identity required");
        if (price == null || price.signum() < 0)
            throw new IllegalArgumentException("Nonnegative price required");
        Objects.requireNonNull(status);
    }
}
