package sugarrush.application.promo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class PromoService {
    public record Applied(String code, BigDecimal discount, BigDecimal total) {
    }

    private record Rule(BigDecimal minimumOrder, LocalDate expiresOn, int percent) {
    }

    private final Map<String, Rule> rules;
    private final Clock clock;

    private PromoService(Map<String, Rule> rules, Clock clock) {
        this.rules = Map.copyOf(rules);
        this.clock = clock;
    }

    public static PromoService disabled() {
        return new PromoService(Map.of(), Clock.systemUTC());
    }

    public PromoService(Properties config) {
        this(config, Clock.system(ZoneId.of(required(config, "promo.zone"))));
    }

    public PromoService(Properties config, Clock clock) {
        Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
        Map<String, Rule> loaded = new LinkedHashMap<>();
        for (String entry : required(config, "promo.codes").split(",", -1)) {
            String code = entry.trim().toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Z0-9_-]{1,32}") || loaded.containsKey(code)) {
                throw new IllegalArgumentException("Invalid or duplicate promo code in configuration");
            }
            String prefix = "promo.code." + code + ".";
            BigDecimal minimum = amount(config, prefix + "minimumOrder");
            LocalDate expiry;
            int percent;
            try {
                expiry = LocalDate.parse(required(config, prefix + "expiresOn"));
                percent = Integer.parseInt(required(config, prefix + "percent"));
            } catch (DateTimeParseException | NumberFormatException invalid) {
                throw new IllegalArgumentException("Invalid promo settings for " + code, invalid);
            }
            if (percent < 1 || percent > 100) {
                throw new IllegalArgumentException("Promo percent must be between 1 and 100 for " + code);
            }
            loaded.put(code, new Rule(minimum, expiry, percent));
        }
        rules = Map.copyOf(loaded);
    }

    public Applied apply(BigDecimal subtotal, BigDecimal fare, String suppliedCode) {
        Objects.requireNonNull(subtotal);
        Objects.requireNonNull(fare);
        if (suppliedCode == null || suppliedCode.isBlank()) {
            return new Applied("", BigDecimal.ZERO.setScale(2), fare);
        }
        String code = suppliedCode.trim().toUpperCase(Locale.ROOT);
        Rule rule = rules.get(code);
        if (rule == null) {
            throw new IllegalArgumentException("Promo code " + code + " is not valid");
        }
        if (LocalDate.now(clock).isAfter(rule.expiresOn())) {
            throw new IllegalArgumentException("Promo code " + code + " expired on " + rule.expiresOn());
        }
        if (subtotal.compareTo(rule.minimumOrder()) < 0) {
            throw new IllegalArgumentException("Promo code " + code + " requires an item subtotal of at least $"
                    + rule.minimumOrder().toPlainString());
        }
        BigDecimal discount = fare.multiply(BigDecimal.valueOf(rule.percent()))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Applied(code, discount, fare.subtract(discount));
    }

    private static String required(Properties config, String key) {
        String value = config.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing promo setting: " + key);
        }
        return value.trim();
    }

    private static BigDecimal amount(Properties config, String key) {
        try {
            BigDecimal value = new BigDecimal(required(config, key)).setScale(2, RoundingMode.UNNECESSARY);
            if (value.signum() < 0) {
                throw new IllegalArgumentException("Negative promo setting: " + key);
            }
            return value;
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("Invalid promo setting: " + key, invalid);
        }
    }
}
