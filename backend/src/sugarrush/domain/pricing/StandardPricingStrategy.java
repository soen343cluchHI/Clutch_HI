package sugarrush.domain.pricing;

import java.math.BigDecimal;
import java.util.Objects;

public final class StandardPricingStrategy implements PricingStrategy {
    private final BigDecimal deliveryFee;
    private final BigDecimal serviceFee;

    public StandardPricingStrategy(BigDecimal deliveryFee, BigDecimal serviceFee) {
        this.deliveryFee = Objects.requireNonNull(deliveryFee);
        this.serviceFee = Objects.requireNonNull(serviceFee);
    }

    @Override
    public BigDecimal calculate(BigDecimal subtotal) {
        return Objects.requireNonNull(subtotal).add(deliveryFee).add(serviceFee);
    }
}
