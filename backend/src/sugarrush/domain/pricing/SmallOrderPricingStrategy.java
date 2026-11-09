package sugarrush.domain.pricing;

import java.math.BigDecimal;
import java.util.Objects;

public final class SmallOrderPricingStrategy implements PricingStrategy {
    private final PricingStrategy standard;
    private final BigDecimal surcharge;

    public SmallOrderPricingStrategy(PricingStrategy standard, BigDecimal surcharge) {
        this.standard = Objects.requireNonNull(standard);
        this.surcharge = Objects.requireNonNull(surcharge);
    }

    @Override
    public BigDecimal calculate(BigDecimal subtotal) {
        return standard.calculate(subtotal).add(surcharge);
    }
}
