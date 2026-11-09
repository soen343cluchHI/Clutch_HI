package sugarrush.domain.pricing;

import java.math.BigDecimal;

public interface PricingStrategy {
    BigDecimal calculate(BigDecimal subtotal);
}
