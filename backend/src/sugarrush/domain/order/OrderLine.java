package sugarrush.domain.order;

import java.math.BigDecimal;
import java.util.Objects;
import sugarrush.domain.restaurant.MenuItem;

public record OrderLine(MenuItem item, int quantity) {
    public OrderLine {
        Objects.requireNonNull(item);
        if (quantity <= 0)
            throw new IllegalArgumentException("Positive quantity required");
        if (item.status() != MenuItem.Status.AVAILABLE)
            throw new IllegalArgumentException("Item sold out");
    }

    public BigDecimal total() {
        return item.price().multiply(BigDecimal.valueOf(quantity));
    }
}
