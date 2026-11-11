package sugarrush.application.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Properties;
import sugarrush.domain.pricing.*;

public final class PricingService {
    // Fare breakdown shown before checkout; total always comes from the same strategies used to charge (R-PRC-01).
    public record Quote(BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal serviceFee,
            BigDecimal smallOrderSurcharge, BigDecimal smallOrderThreshold, BigDecimal total) {
    }

    private final BigDecimal deliveryFee, serviceFee, surcharge;
    private final BigDecimal smallOrderThreshold;
    private final PricingStrategy standard;
    private final PricingStrategy smallOrder;

    public PricingService(Properties config) {
        Objects.requireNonNull(config);
        deliveryFee = amount(config, "pricing.deliveryFee");
        serviceFee = amount(config, "pricing.serviceFee");
        smallOrderThreshold = amount(config, "pricing.smallOrderThreshold");
        surcharge = amount(config, "pricing.smallOrderSurcharge");
        standard = new StandardPricingStrategy(deliveryFee, serviceFee);
        smallOrder = new SmallOrderPricingStrategy(standard, surcharge);
    }

    public BigDecimal calculate(BigDecimal subtotal) {
        Objects.requireNonNull(subtotal);
        if (subtotal.signum() < 0) throw new IllegalArgumentException("Subtotal must be nonnegative");
        BigDecimal money = subtotal.setScale(2, RoundingMode.UNNECESSARY);
        PricingStrategy strategy = money.compareTo(smallOrderThreshold) < 0 ? smallOrder : standard;
        return strategy.calculate(money).setScale(2, RoundingMode.UNNECESSARY);
    }

    public Quote quote(BigDecimal subtotal) {
        BigDecimal total = calculate(subtotal);
        BigDecimal money = subtotal.setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal applied = money.compareTo(smallOrderThreshold) < 0 ? surcharge : BigDecimal.ZERO.setScale(2);
        return new Quote(money, deliveryFee, serviceFee, applied, smallOrderThreshold, total);
    }

    private static BigDecimal amount(Properties config, String key) {
        String value = config.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing pricing field: " + key);
        try {
            BigDecimal amount = new BigDecimal(value.trim()).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() < 0) throw new IllegalArgumentException("Negative pricing field: " + key);
            return amount;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid pricing field: " + key, e);
        }
    }
}
